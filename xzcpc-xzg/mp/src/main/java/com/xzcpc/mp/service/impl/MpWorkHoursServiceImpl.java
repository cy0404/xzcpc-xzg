package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.mp.dto.WorkHoursSaveReq;
import com.xzcpc.mp.entity.StoreWorkHours;
import com.xzcpc.mp.mapper.StoreWorkHoursMapper;
import com.xzcpc.mp.service.MpStaffService;
import com.xzcpc.mp.service.MpWorkHoursService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MpWorkHoursServiceImpl implements MpWorkHoursService {

    private static final Set<String> ALLOWED_ROLES = Set.of("老板", "店长");

    private final StoreWorkHoursMapper workHoursMapper;
    private final MpStaffService staffService;

    @Override
    public List<StoreWorkHours> list(String storeId) {
        requireStore(storeId);
        return workHoursMapper.selectList(new LambdaQueryWrapper<StoreWorkHours>()
                .eq(StoreWorkHours::getStoreId, storeId)
                .orderByDesc(StoreWorkHours::getRecordTime)
                .orderByDesc(StoreWorkHours::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreWorkHours create(String storeId, String storeName, String openid, WorkHoursSaveReq req) {
        Map<String, Object> profile = requireManagerProfile(openid, storeId);
        requireStore(storeId);
        String yearMonth = normalizeYearMonth(req.getRecordTime());
        BigDecimal hours = normalizeHours(req.getHours());
        if (hours.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "工时须大于 0");
        }
        if (existsForMonth(storeId, yearMonth, null)) {
            throw new BusinessException(400, "该月已录入");
        }

        StoreWorkHours record = new StoreWorkHours();
        record.setStoreId(storeId);
        record.setStoreName(StringUtils.hasText(storeName) ? storeName : "未知门店");
        record.setRecordTime(yearMonth);
        record.setHours(hours);
        record.setEmployeeId((String) profile.getOrDefault("employeeId", ""));
        record.setEmployeeName((String) profile.getOrDefault("employeeName", ""));
        record.setRecordId("TMP");
        workHoursMapper.insert(record);
        record.setRecordId("WH" + String.format("%08d", record.getId()));
        workHoursMapper.updateById(record);
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreWorkHours update(String storeId, String openid, String recordId, WorkHoursSaveReq req) {
        requireManagerProfile(openid, storeId);
        StoreWorkHours record = detail(storeId, recordId);
        String yearMonth = normalizeYearMonth(req.getRecordTime());
        BigDecimal hours = normalizeHours(req.getHours());
        if (hours.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "工时不能为负数");
        }
        if (existsForMonth(storeId, yearMonth, recordId)) {
            throw new BusinessException(400, "该月已录入");
        }
        record.setRecordTime(yearMonth);
        record.setHours(hours);
        workHoursMapper.updateById(record);
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String storeId, String openid, String recordId) {
        requireManagerProfile(openid, storeId);
        StoreWorkHours record = detail(storeId, recordId);
        workHoursMapper.deleteById(record.getId());
    }

    private StoreWorkHours detail(String storeId, String recordId) {
        requireStore(storeId);
        StoreWorkHours record = workHoursMapper.selectOne(new LambdaQueryWrapper<StoreWorkHours>()
                .eq(StoreWorkHours::getStoreId, storeId)
                .eq(StoreWorkHours::getRecordId, recordId));
        if (record == null) {
            throw new BusinessException(404, "工时记录不存在");
        }
        return record;
    }

    private boolean existsForMonth(String storeId, String yearMonth, String excludeRecordId) {
        LambdaQueryWrapper<StoreWorkHours> wrapper = new LambdaQueryWrapper<StoreWorkHours>()
                .eq(StoreWorkHours::getStoreId, storeId)
                .eq(StoreWorkHours::getRecordTime, yearMonth);
        if (StringUtils.hasText(excludeRecordId)) {
            wrapper.ne(StoreWorkHours::getRecordId, excludeRecordId);
        }
        return workHoursMapper.selectCount(wrapper) > 0;
    }

    private Map<String, Object> requireManagerProfile(String openid, String storeId) {
        Map<String, Object> profile = staffService.currentStaffProfile(openid, storeId);
        String role = (String) profile.getOrDefault("role", "");
        if (!ALLOWED_ROLES.contains(role)) {
            throw new BusinessException(403, "仅老板、店长可录入工时");
        }
        return profile;
    }

    private void requireStore(String storeId) {
        if (!StringUtils.hasText(storeId)) {
            throw new BusinessException(403, "请先绑定门店");
        }
    }

    private String normalizeYearMonth(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(400, "请选择年月");
        }
        String ym = raw.trim();
        if (!ym.matches("\\d{4}-\\d{2}")) {
            throw new BusinessException(400, "年月格式不正确");
        }
        return ym;
    }

    private BigDecimal normalizeHours(BigDecimal hours) {
        if (hours == null) {
            throw new BusinessException(400, "请输入工时");
        }
        return hours.setScale(2, RoundingMode.HALF_UP);
    }
}
