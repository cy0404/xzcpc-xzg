package com.xzcpc.mp.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.mp.entity.OwnerBindApplication;
import com.xzcpc.mp.entity.OwnerRegistration;
import com.xzcpc.mp.entity.StoreContact;
import com.xzcpc.mp.mapper.OwnerBindApplicationMapper;
import com.xzcpc.mp.mapper.OwnerRegistrationMapper;
import com.xzcpc.mp.mapper.StoreContactMapper;
import com.xzcpc.mp.service.OwnerRegisterService;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.mapper.EmployeeMapper;
import com.xzcpc.task.service.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerRegisterServiceImpl implements OwnerRegisterService {


    private final WxMaService wxMaService;
    private final StoreService storeService;
    private final StoreContactMapper contactMapper;
    private final OwnerBindApplicationMapper bindApplicationMapper;
    private final OwnerRegistrationMapper registrationMapper;
    private final OwnerRegistrationRecordService registrationRecordService;
    private final EmployeeMapper employeeMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Override
    public List<Map<String, Object>> listStores() {
        List<StoreInfo> stores = storeService.getAllStores();
        List<Map<String, Object>> result = new ArrayList<>();
        for (StoreInfo s : stores) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("storeId", s.getId());
            m.put("storeName", s.getMendianmingcheng());
            result.add(m);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> submit(String code, String name, String phone,
                                             String role, String bindCode, List<String> storeIds) {
        if (bindCode == null || bindCode.isBlank()) {
            throw new BusinessException(400, "二维码无效");
        }
        if (storeIds == null || storeIds.isEmpty()) {
            throw new BusinessException(400, "请选择门店");
        }
        validateBindCodeNotExpired(bindCode);

        String openid = resolveOpenid(code);
        String trimmedName = name.trim();
        String trimmedPhone = phone.trim();
        String effectiveRole = StringUtils.hasText(role) ? role.trim() : "店长";

        List<Map<String, Object>> results = new ArrayList<>();
        for (String storeId : storeIds) {
            if (!StringUtils.hasText(storeId)) {
                continue;
            }
            Map<String, Object> item = transactionTemplate.execute(status ->
                    processOneStore(openid, trimmedName, trimmedPhone, effectiveRole, bindCode, storeId.trim()));
            if (item != null) {
                results.add(item);
            }
        }
        return results;
    }

    private void validateBindCodeNotExpired(String bindCode) {
        OwnerBindApplication app = bindApplicationMapper.selectOne(new LambdaQueryWrapper<OwnerBindApplication>()
                .eq(OwnerBindApplication::getBindCode, bindCode)
                .last("LIMIT 1"));
        if (app == null) {
            throw new BusinessException(400, "二维码无效");
        }
        if (app.getExpireAt() != null && app.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "二维码已过期");
        }
    }

    private String resolveOpenid(String code) {
        try {
            WxMaJscode2SessionResult r = wxMaService.jsCode2SessionInfo(code);
            if (!StringUtils.hasText(r.getOpenid())) {
                throw new BusinessException(400, "微信授权失败");
            }
            return r.getOpenid();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("换取 openid 失败", e);
            throw new BusinessException(400, "微信授权失败");
        }
    }

    private Map<String, Object> processOneStore(String openid, String name, String phone,
                                                 String role, String bindCode, String storeId) {
        StoreInfo store = storeService.getStoreById(storeId);
        String storeName = store != null ? store.getMendianmingcheng() : storeId;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("storeId", storeId);
        item.put("storeName", storeName);

        OwnerRegistration reg = new OwnerRegistration();
        reg.setOpenid(openid);
        reg.setBindCode(bindCode);
        reg.setName(name);
        reg.setPhone(phone);
        reg.setRole(role);
        reg.setStoreId(storeId);
        reg.setStoreName(storeName);
        reg.setStatus("未关联");
        registrationRecordService.saveAttempt(reg);

        if (store == null) {
            item.put("status", "未关联");
            return item;
        }

        StoreContact contact = contactMapper.selectOne(new LambdaQueryWrapper<StoreContact>()
                .eq(StoreContact::getStoreId, storeId)
                .eq(StoreContact::getContactName, name)
                .eq(StoreContact::getContactPhone, phone)
                .last("LIMIT 1"));

        if (contact == null) {
            item.put("status", "未关联");
            return item;
        }

        if ("老板".equals(role)) {
            storeService.updateOwnerInfo(storeId, openid, name, phone);
        }

        registrationRecordService.updateStatus(reg.getId(), "已绑定");
        upsertEmployee(storeId, storeName, openid, name, phone, role);
        item.put("status", "已绑定");
        return item;
    }

    private void upsertEmployee(String storeId, String storeName, String openid,
                                 String name, String phone, String role) {
        Employee emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStoreId, storeId)
                .eq(Employee::getOpenid, openid)
                .last("LIMIT 1"));
        if (emp == null) {
            emp = new Employee();
            emp.setEmployeeId("TMP");
            emp.setStoreId(storeId);
            emp.setStoreName(storeName);
            emp.setStatus("在职");
            emp.setEmploymentType("全职");
            emp.setOpenid(openid);
            emp.setName(name);
            emp.setMobile(phone);
            emp.setRole(role);
            if (emp.getEntryDate() == null) {
                emp.setEntryDate(LocalDate.now());
            }
            try {
                employeeMapper.insert(emp);
                emp.setEmployeeId("EMP" + String.format("%08d", emp.getId()));
                employeeMapper.updateById(emp);
            } catch (DuplicateKeyException e) {
                // 已离职或软删除的员工：物理删旧记录，重新插入
                jdbcTemplate.update("DELETE FROM employee WHERE store_id=? AND openid=?", storeId, openid);
                employeeMapper.insert(emp);
                emp.setEmployeeId("EMP" + String.format("%08d", emp.getId()));
                employeeMapper.updateById(emp);
            }
        } else {
            updateEmployeeFields(emp, storeName, name, phone, role);
        }
    }

    private void updateEmployeeFields(Employee emp, String storeName, String name, String phone, String role) {
        emp.setStoreName(storeName);
        emp.setName(name);
        emp.setMobile(phone);
        emp.setRole(role);
        if (emp.getEntryDate() == null) {
            emp.setEntryDate(LocalDate.now());
        }
        employeeMapper.updateById(emp);
    }
}
