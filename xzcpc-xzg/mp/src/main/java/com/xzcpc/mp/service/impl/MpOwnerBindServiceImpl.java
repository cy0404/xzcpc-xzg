package com.xzcpc.mp.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.chanjar.weixin.common.error.WxErrorException;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.mp.dto.OwnerBindReq;
import com.xzcpc.mp.dto.OwnerBindStatusResp;
import com.xzcpc.mp.dto.OwnerMyStatusResp;
import com.xzcpc.mp.entity.OwnerBindApplication;
import com.xzcpc.mp.mapper.OwnerBindApplicationMapper;
import com.xzcpc.mp.service.MpOwnerBindService;
import com.xzcpc.task.entity.Store;
import com.xzcpc.task.mapper.StoreMapper;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import java.io.File;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpOwnerBindServiceImpl implements MpOwnerBindService {

    private final WxMaService wxMaService;
    private final OwnerBindApplicationMapper bindMapper;
    private final StoreMapper storeMapper;
    private final EmployeeMapper employeeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OwnerBindStatusResp submitBind(OwnerBindReq req) {
        // 1. 校验绑定码（任何一条记录有这个 bindCode 且未过期即有效）
        OwnerBindApplication template = bindMapper.selectOne(
                new LambdaQueryWrapper<OwnerBindApplication>()
                        .eq(OwnerBindApplication::getBindCode, req.getBindCode())
                        .gt(OwnerBindApplication::getExpireAt, LocalDateTime.now())
                        .last("LIMIT 1"));
        if (template == null) {
            throw new BusinessException("绑定码无效或已过期");
        }

        // 2. 通过 wxCode 换取 openid
        String openid;
        try {
            WxMaJscode2SessionResult result = wxMaService.jsCode2SessionInfo(req.getWxCode());
            openid = result.getOpenid();
        } catch (Exception e) {
            log.error("换取 openid 失败", e);
            throw new BusinessException("微信授权失败，请重试");
        }

        // 3. 解析生日
        LocalDate birthday;
        try {
            birthday = LocalDate.parse(req.getBirthday(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new BusinessException("生日格式不正确");
        }

        // 4. 自动匹配：手机号 + 生日 查 store_info 表
        List<Store> matchedStores = storeMapper.selectList(
                new LambdaQueryWrapper<Store>()
                        .eq(Store::getOwnerPhone, req.getPhone())
                        .eq(Store::getOwnerBirthday, req.getBirthday()));

        // 5. 筛选出新门店（owner_openid 为空或不同于当前微信的）
        List<Store> newStores = matchedStores.stream()
                .filter(s -> s.getOwnerOpenid() == null || !s.getOwnerOpenid().equals(openid))
                .toList();

        // 6. 查找此人此码是否已有申请（允许覆盖）
        OwnerBindApplication app = bindMapper.selectOne(
                new LambdaQueryWrapper<OwnerBindApplication>()
                        .eq(OwnerBindApplication::getBindCode, req.getBindCode())
                        .eq(OwnerBindApplication::getWechatOpenid, openid));
        boolean isNew = (app == null);
        if (isNew) {
            app = new OwnerBindApplication();
            app.setBindCode(req.getBindCode());
            app.setExpireAt(template.getExpireAt());
        }
        app.setWechatOpenid(openid);
        app.setName(req.getName());
        app.setPhone(req.getPhone());
        app.setBirthday(birthday);

        if (!newStores.isEmpty()) {
            // ====== 🟢 自动绑定新门店 ======
            List<String> storeIds = newStores.stream()
                    .map(Store::getStoreId)
                    .collect(Collectors.toList());
            List<String> storeNames = newStores.stream()
                    .map(Store::getStoreName)
                    .collect(Collectors.toList());

            // 合并已有门店记录 + 新门店
            String mergedIds = String.join(",", storeIds);
            if (app.getMatchStoreIds() != null && !app.getMatchStoreIds().isEmpty()) {
                mergedIds = app.getMatchStoreIds() + "," + mergedIds;
            }
            app.setMatchStoreIds(mergedIds);
            app.setBindStatus("auto_bound");
            app.setAutoBound(1);
            if (isNew) bindMapper.insert(app); else bindMapper.updateById(app);

            // 回填新门店的 owner_openid
            for (Store store : newStores) {
                store.setOwnerOpenid(openid);
                storeMapper.updateById(store);
            }

            // 为新门店写入 employee 记录（role=老板）
            for (Store store : newStores) {
                long existCount = employeeMapper.selectCount(
                        new LambdaQueryWrapper<Employee>()
                                .eq(Employee::getOpenid, openid)
                                .eq(Employee::getStoreId, store.getStoreId())
                                .eq(Employee::getStatus, "在职"));
                if (existCount == 0) {
                    Employee emp = new Employee();
                    emp.setEmployeeId("OWN" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000));
                    emp.setName(req.getName());
                    emp.setMobile(req.getPhone());
                    emp.setOpenid(openid);
                    emp.setStoreId(store.getStoreId());
                    emp.setStoreName(store.getStoreName());
                    emp.setRole("老板");
                    emp.setStatus("在职");
                    emp.setEmploymentType("全职");
                    emp.setEntryDate(birthday);
                    emp.setBirthday(birthday);
                    employeeMapper.insert(emp);
                    log.info("写入老板employee记录 openid={} storeId={}", openid, store.getStoreId());
                }
            }

            log.info("老板自动绑定成功 openid={} phone={} matchedStores={}",
                    openid, maskPhone(req.getPhone()), storeIds);

            return OwnerBindStatusResp.builder()
                    .status("auto_bound")
                    .message("绑定成功，欢迎回来")
                    .storeCount(storeIds.size())
                    .storeNames(storeNames)
                    .name(req.getName())
                    .phoneMasked(maskPhone(req.getPhone()))
                    .submitTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    .build();

        } else if (!matchedStores.isEmpty()) {
            // ====== 🟡 所有匹配门店已绑定过当前微信 ======
            app.setBindStatus("auto_bound");
            if (isNew) bindMapper.insert(app); else bindMapper.updateById(app);
            throw new BusinessException("该微信已绑定过门店，请直接登录");
        } else {
            // ====== 🟠 没有匹配到任何门店 → 进入总部审核 ======
            app.setBindStatus("pending");
            app.setAutoBound(0);
            if (isNew) bindMapper.insert(app); else bindMapper.updateById(app);

            log.info("老板绑定申请待审核 openid={} phone={}", openid, maskPhone(req.getPhone()));

            return OwnerBindStatusResp.builder()
                    .status("pending")
                    .message("信息已提交，等待总部核验")
                    .name(req.getName())
                    .phoneMasked(maskPhone(req.getPhone()))
                    .submitTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    .build();
        }
    }

    @Override
    public OwnerBindStatusResp getBindStatus(String bindCode) {
        // 多老板场景下同一 bindCode 可能有多条记录，取最新一条
        OwnerBindApplication app = bindMapper.selectOne(
                new LambdaQueryWrapper<OwnerBindApplication>()
                        .eq(OwnerBindApplication::getBindCode, bindCode)
                        .orderByDesc(OwnerBindApplication::getCreatedAt)
                        .last("LIMIT 1"));
        if (app == null) {
            throw new BusinessException("绑定码无效");
        }

        String status = app.getBindStatus();
        String message;
        List<String> storeNames = Collections.emptyList();

        switch (status) {
            case "auto_bound":
            case "approved":
                // 重新查询门店信息（可能已有变化）
                if (app.getMatchStoreIds() != null && !app.getMatchStoreIds().isEmpty()) {
                    String[] ids = app.getMatchStoreIds().split(",");
                    storeNames = java.util.Arrays.stream(ids)
                            .map(id -> {
                                Store s = storeMapper.selectOne(
                                        new LambdaQueryWrapper<Store>().eq(Store::getStoreId, id));
                                return s != null ? s.getStoreName() : id;
                            })
                            .collect(Collectors.toList());
                }
                message = "auto_bound".equals(status) ? "绑定成功，欢迎回来" : "审核通过";
                break;
            case "rejected":
                message = "审核未通过";
                break;
            default:
                message = "等待总部核验中";
                break;
        }

        return OwnerBindStatusResp.builder()
                .status(status)
                .message(message)
                .storeCount(storeNames.size())
                .storeNames(storeNames)
                .name(app.getName())
                .phoneMasked(maskPhone(app.getPhone()))
                .submitTime(app.getCreatedAt() != null
                        ? app.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : "")
                .rejectReason(app.getRejectReason())
                .build();
    }

    @Override
    public byte[] generateQrCode(String bindCode) {
        // 确认 bindCode 已存在
        OwnerBindApplication app = bindMapper.selectOne(
                new LambdaQueryWrapper<OwnerBindApplication>()
                        .eq(OwnerBindApplication::getBindCode, bindCode));
        if (app == null) {
            throw new BusinessException("绑定码不存在，请先生成");
        }

        try {
            File qrFile = wxMaService.getQrcodeService()
                    .createWxaCodeUnlimit(bindCode, "pages/bind/owner-register");
            byte[] qrBytes = Files.readAllBytes(qrFile.toPath());
            log.info("生成小程序码成功 bindCode={}", bindCode);
            return qrBytes;
        } catch (Exception e) {
            log.error("生成小程序码失败 bindCode={}", bindCode, e);
            throw new BusinessException("生成二维码失败: " + e.getMessage());
        }
    }

    @Override
    public OwnerMyStatusResp getMyStatus(String wxCode) {
        // 1. wxCode → openid
        String openid;
        try {
            WxMaJscode2SessionResult result = wxMaService.jsCode2SessionInfo(wxCode);
            openid = result.getOpenid();
        } catch (WxErrorException e) {
            log.error("换取 openid 失败", e);
            throw new BusinessException("微信授权失败，请重试");
        }

        // 2. 查已绑定的门店
        List<Store> boundStores = storeMapper.selectList(
                new LambdaQueryWrapper<Store>()
                        .eq(Store::getOwnerOpenid, openid));
        int boundCount = boundStores.size();

        // 3. 查最新一条绑定申请
        OwnerBindApplication latestApp = bindMapper.selectOne(
                new LambdaQueryWrapper<OwnerBindApplication>()
                        .eq(OwnerBindApplication::getWechatOpenid, openid)
                        .orderByDesc(OwnerBindApplication::getCreatedAt)
                        .last("LIMIT 1"));

        OwnerMyStatusResp.LatestApplication latest = null;
        if (latestApp != null) {
            List<String> storeNames = Collections.emptyList();
            if (latestApp.getMatchStoreIds() != null && !latestApp.getMatchStoreIds().isEmpty()) {
                String[] ids = latestApp.getMatchStoreIds().split(",");
                storeNames = java.util.Arrays.stream(ids)
                        .map(id -> {
                            Store s = storeMapper.selectOne(
                                    new LambdaQueryWrapper<Store>().eq(Store::getStoreId, id));
                            return s != null ? s.getStoreName() : id;
                        })
                        .collect(Collectors.toList());
            }
            latest = OwnerMyStatusResp.LatestApplication.builder()
                    .bindCode(latestApp.getBindCode())
                    .status(latestApp.getBindStatus())
                    .storeNames(storeNames)
                    .rejectReason(latestApp.getRejectReason())
                    .submitTime(latestApp.getCreatedAt() != null
                            ? latestApp.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            : "")
                    .name(latestApp.getName())
                    .phoneMasked(maskPhone(latestApp.getPhone()))
                    .build();
        }

        return OwnerMyStatusResp.builder()
                .hasBound(boundCount > 0)
                .boundStoreCount(boundCount)
                .latestApplication(latest)
                .build();
    }

    @Override
    public OwnerBindStatusResp getLatestApplicationByOpenid(String openid) {
        OwnerBindApplication latest = bindMapper.selectOne(
                new LambdaQueryWrapper<OwnerBindApplication>()
                        .eq(OwnerBindApplication::getWechatOpenid, openid)
                        .orderByDesc(OwnerBindApplication::getCreatedAt)
                        .last("LIMIT 1"));

        if (latest == null) {
            return null;
        }

        List<String> storeNames = Collections.emptyList();
        if (latest.getMatchStoreIds() != null && !latest.getMatchStoreIds().isEmpty()) {
            String[] ids = latest.getMatchStoreIds().split(",");
            storeNames = java.util.Arrays.stream(ids)
                    .map(id -> {
                        Store s = storeMapper.selectOne(
                                new LambdaQueryWrapper<Store>().eq(Store::getStoreId, id));
                        return s != null ? s.getStoreName() : id;
                    })
                    .collect(Collectors.toList());
        }

        return OwnerBindStatusResp.builder()
                .status(latest.getBindStatus())
                .storeCount(storeNames.size())
                .storeNames(storeNames)
                .name(latest.getName())
                .phoneMasked(maskPhone(latest.getPhone()))
                .rejectReason(latest.getRejectReason())
                .submitTime(latest.getCreatedAt() != null
                        ? latest.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : "")
                .build();
    }

    // ==================== 工具方法 ====================

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
