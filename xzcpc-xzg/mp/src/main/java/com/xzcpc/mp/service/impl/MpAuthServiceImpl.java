package com.xzcpc.mp.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xzcpc.common.entity.LoginLog;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.common.service.LoginLogService;
import com.xzcpc.mp.dto.BindStoreReq;
import com.xzcpc.mp.dto.LoginResp;
import com.xzcpc.mp.entity.StoreManagerSession;
import com.xzcpc.mp.mapper.StoreManagerSessionMapper;
import com.xzcpc.mp.service.MpAuthService;
import com.xzcpc.mp.service.MpStaffService;
import com.xzcpc.mp.util.JwtUtil;
import com.xzcpc.task.service.StoreService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpAuthServiceImpl implements MpAuthService {

    private final WxMaService wxMaService;
    private final StoreManagerSessionMapper sessionMapper;
    private final JwtUtil jwtUtil;
    private final StoreService storeService;
    private final LoginLogService loginLogService;
    private final MpStaffService staffService;

    @Override
    public LoginResp wxLogin(String code, String wxNickname) {
        String openid;
        String sessionKey;
        try {
            WxMaJscode2SessionResult result = wxMaService.jsCode2SessionInfo(code);
            openid = result.getOpenid();
            sessionKey = result.getSessionKey();
        } catch (Exception e) {
            log.error("wx.login 换 openid 失败", e);
            saveLoginLog("wx_login", null, null, 0, "微信登录失败: " + e.getMessage());
            throw new BusinessException("微信登录失败，请重试");
        }

        StoreManagerSession session = sessionMapper.selectOne(
                new LambdaQueryWrapper<StoreManagerSession>().eq(StoreManagerSession::getOpenid, openid));

        if (session == null) {
            session = new StoreManagerSession();
            session.setOpenid(openid);
            sessionMapper.insert(session);
        }

        session.setSessionKey(sessionKey);
        if (StringUtils.hasText(wxNickname)) {
            session.setWxNickname(wxNickname);
        }

        // 根据 openid 自动匹配门店（查询 Employee 表，老板绑定后也在这里匹配到）
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(openid);

        if (!stores.isEmpty()) {
            Map<String, Object> primary = stores.get(0);
            session.setStoreId((String) primary.get("storeId"));
            session.setStoreName((String) primary.get("storeName"));
        } else if (session.getId() != null) {
            // 已有会话但 findStoresByOpenid 返回空 → 员工已离职，清除旧的门店绑定
            session.setStoreId(null);
            session.setStoreName(null);
        }

        // 一次性写入所有变更
        boolean bound = StringUtils.hasText(session.getStoreId());
        String token = jwtUtil.generate(session.getId().longValue(), openid);
        session.setToken(token);
        session.setLastLoginAt(LocalDateTime.now());
        sessionMapper.updateById(session);

        saveLoginLog("login", session.getId().longValue(), session.getWxNickname(), 1, null);

        LoginResp resp = new LoginResp(
                token,
                bound,
                session.getStoreId(),
                session.getStoreName()
        );
        resp.setStoreCount(stores.isEmpty() ? 1 : stores.size());
        resp = withStaffProfile(resp, session);
        // 已离职员工：staffBound=false，视为未绑定，前端会跳到登录页
        if (!resp.isStaffBound()) {
            resp.setBound(false);
            resp.setRole("");
            resp.setPermissions(List.of());
        }
        return resp;
    }

    @Override
    public LoginResp bindStore(Long sessionId, BindStoreReq req) {
        StoreManagerSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(401, "会话已失效");
        }

        StoreInfo store = storeService.getStoreById(req.getStoreId());
        if (store == null) {
            throw new BusinessException("门店不存在，请重新选择");
        }

        session.setStoreId(store.getId());
        session.setStoreName(store.getMendianmingcheng());
        String token = jwtUtil.generate(sessionId, session.getOpenid());
        session.setToken(token);
        session.setLastLoginAt(LocalDateTime.now());
        sessionMapper.updateById(session);

        saveLoginLog("bind_store", sessionId, session.getWxNickname(), 1, null);

        return withStaffProfile(new LoginResp(
                token,
                true,
                session.getStoreId(),
                session.getStoreName()
        ), session);
    }

    @Override
    public LoginResp switchStore(Long sessionId, BindStoreReq req) {
        StoreManagerSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(401, "会话已失效");
        }

        // 校验该门店属于当前 openid 的关联门店
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(session.getOpenid());
        boolean belongsToUser = stores.stream()
                .anyMatch(s -> req.getStoreId().equals(s.get("storeId")));
        if (!belongsToUser) {
            throw new BusinessException("无权访问该门店");
        }

        StoreInfo store = storeService.getStoreById(req.getStoreId());
        if (store == null) {
            throw new BusinessException("门店不存在");
        }

        session.setStoreId(store.getId());
        session.setStoreName(store.getMendianmingcheng());
        String token = jwtUtil.generate(sessionId, session.getOpenid());
        session.setToken(token);
        session.setLastLoginAt(LocalDateTime.now());
        sessionMapper.updateById(session);

        saveLoginLog("switch_store", sessionId, session.getWxNickname(), 1, null);

        LoginResp resp = new LoginResp(
                token,
                true,
                session.getStoreId(),
                session.getStoreName()
        );
        resp.setStoreCount(stores.size());
        return withStaffProfile(resp, session);
    }

    @Override
    public void logout(Long sessionId) {
        StoreManagerSession session = sessionMapper.selectById(sessionId);
        String nickname = session != null ? session.getWxNickname() : null;
        saveLoginLog("logout", sessionId, nickname, 1, null);

        sessionMapper.update(null,
            new LambdaUpdateWrapper<StoreManagerSession>()
                .eq(StoreManagerSession::getId, sessionId)
                .set(StoreManagerSession::getToken, null)
                .set(StoreManagerSession::getStoreId, null)
                .set(StoreManagerSession::getStoreName, null));
    }

    @Override
    public List<Map<String, Object>> myStores(Long sessionId) {
        StoreManagerSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return List.of();
        }
        return staffService.findStoresByOpenid(session.getOpenid());
    }

    // ---------- private ----------

    private void saveLoginLog(String loginType, Long userId, String username, int status, String failReason) {
        try {
            LoginLog loginLog = new LoginLog();
            loginLog.setLoginType(loginType);
            loginLog.setUserId(userId);
            loginLog.setUsername(username != null ? username : "");
            loginLog.setStatus(status);
            loginLog.setFailReason(failReason != null ? failReason : "");
            loginLog.setCreatedAt(LocalDateTime.now());

            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                loginLog.setRequestIp(getClientIp(request));
                loginLog.setUserAgent(request.getHeader("User-Agent"));
            }
            loginLogService.save(loginLog);
        } catch (Exception e) {
            log.error("保存登录日志失败", e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    @Override
    public LoginResp me(Long sessionId) {
        StoreManagerSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(401, "会话已失效");
        }
        boolean bound = StringUtils.hasText(session.getStoreId());
        LoginResp resp = new LoginResp(
                session.getToken(),
                bound,
                session.getStoreId(),
                session.getStoreName()
        );
        resp = withStaffProfile(resp, session);
        // 已离职员工：staffBound=false，视为未绑定，前端会跳到登录页
        if (!resp.isStaffBound()) {
            resp.setBound(false);
            resp.setRole("");
            resp.setPermissions(List.of());
        }
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(session.getOpenid());
        resp.setStoreCount(stores.isEmpty() ? 1 : stores.size());
        return resp;
    }

    private LoginResp withStaffProfile(LoginResp resp, StoreManagerSession session) {
        if (session == null || !StringUtils.hasText(session.getStoreId())) {
            return resp;
        }
        Map<String, Object> profile = staffService.currentStaffProfile(session.getOpenid(), session.getStoreId());
        resp.setEmployeeId((String) profile.getOrDefault("employeeId", ""));
        resp.setEmployeeName((String) profile.getOrDefault("employeeName", ""));
        resp.setRole((String) profile.getOrDefault("role", ""));
        resp.setStaffBound(Boolean.TRUE.equals(profile.get("staffBound")));
        Object permissions = profile.get("permissions");
        if (permissions instanceof List<?> list) {
            resp.setPermissions(list.stream().map(String::valueOf).toList());
        }
        return resp;
    }
}
