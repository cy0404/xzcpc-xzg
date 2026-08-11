package com.xzcpc.people.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.service.StoreAccessService;
import com.xzcpc.people.dto.OwnerRegistrationSaveReq;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.entity.OwnerRegistration;
import com.xzcpc.people.entity.StoreContact;
import com.xzcpc.people.mapper.EmployeeMapper;
import com.xzcpc.people.mapper.OwnerRegistrationMapper;
import com.xzcpc.people.mapper.StoreContactMapper;
import com.xzcpc.people.service.OwnerRegistrationService;
import com.xzcpc.task.entity.Store;
import com.xzcpc.task.mapper.StoreMapper;
import com.xzcpc.task.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OwnerRegistrationServiceImpl implements OwnerRegistrationService {

    private static final Set<String> ALLOWED_ROLES = Set.of("老板", "店长");

    private final OwnerRegistrationMapper registrationMapper;
    private final StoreContactMapper contactMapper;
    private final EmployeeMapper employeeMapper;
    private final StoreMapper storeMapper;
    private final StoreService storeService;
    private final StoreAccessService storeAccessService;

    @Override
    public Page<OwnerRegistration> page(String storeId, String supervisorName, String status, String name, String phone,
                                        int pageNum, int pageSize) {
        LambdaQueryWrapper<OwnerRegistration> wrapper = new LambdaQueryWrapper<>();

        // 督导角色：按可访问门店过滤
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            java.util.List<String> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            wrapper.in(OwnerRegistration::getStoreId, accessibleStoreIds);
        }

        // 按指定督导过滤
        if (StringUtils.hasText(supervisorName)) {
            java.util.List<String> supervisorStoreIds = storeAccessService.getAccessibleStoreIdsBySupervisorName(supervisorName);
            if (supervisorStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            wrapper.in(OwnerRegistration::getStoreId, supervisorStoreIds);
        }

        if (StringUtils.hasText(storeId)) {
            wrapper.eq(OwnerRegistration::getStoreId, storeId.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(OwnerRegistration::getStatus, status.trim());
        }
        if (StringUtils.hasText(name)) {
            wrapper.like(OwnerRegistration::getName, name.trim());
        }
        if (StringUtils.hasText(phone)) {
            wrapper.like(OwnerRegistration::getPhone, phone.trim());
        }
        wrapper.orderByDesc(OwnerRegistration::getCreatedAt).orderByDesc(OwnerRegistration::getId);
        Page<OwnerRegistration> result = registrationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<OwnerRegistration> records = result.getRecords();
        if (!records.isEmpty()) {
            Set<String> storeIds = records.stream().map(OwnerRegistration::getStoreId).filter(id -> id != null).collect(java.util.stream.Collectors.toSet());
            if (!storeIds.isEmpty()) {
                Map<String, String> supervisorMap = storeAccessService.getSupervisorNamesByStoreIds(storeIds);
                records.forEach(r -> r.setSupervisorName(supervisorMap.getOrDefault(r.getStoreId(), "")));
            }
        }
        return result;
    }

    @Override
    public OwnerRegistration detail(Long id) {
        OwnerRegistration reg = registrationMapper.selectById(id);
        if (reg == null) {
            throw new BusinessException(404, "登记记录不存在");
        }

        // 督导角色校验：只能查看自己管辖门店的记录
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin) && reg.getStoreId() != null) {
            List<String> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleStoreIds.isEmpty() || !accessibleStoreIds.contains(reg.getStoreId())) {
                throw new BusinessException(404, "登记记录不存在");
            }
        }

        return reg;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OwnerRegistration bind(Long id, OwnerRegistrationSaveReq req) {
        OwnerRegistration reg = detail(id);
        if (!"未关联".equals(reg.getStatus())) {
            throw new BusinessException(400, "仅未关联记录可执行手动绑定");
        }
        applyBinding(reg, req, true);
        return reg;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OwnerRegistration update(Long id, OwnerRegistrationSaveReq req) {
        OwnerRegistration reg = detail(id);
        if (!"已绑定".equals(reg.getStatus())) {
            throw new BusinessException(400, "仅已绑定记录可编辑，未关联请使用手动绑定");
        }
        applyBinding(reg, req, false);
        return reg;
    }

    private void applyBinding(OwnerRegistration reg, OwnerRegistrationSaveReq req, boolean removeDuplicates) {
        String name = req.getName().trim();
        String phone = req.getPhone().trim();
        String role = req.getRole().trim();
        if (!ALLOWED_ROLES.contains(role)) {
            throw new BusinessException(400, "角色仅支持：老板、店长");
        }

        String storeId = reg.getStoreId();
        String openid = reg.getOpenid();
        Store store = storeMapper.selectOne(new LambdaQueryWrapper<Store>().eq(Store::getStoreId, storeId));
        if (store == null) {
            throw new BusinessException(404, "门店不存在");
        }

        if ("老板".equals(role)) {
            storeService.updateOwnerInfo(store, openid, name, phone);
        } else {
            if (StringUtils.hasText(store.getOwnerOpenid()) && store.getOwnerOpenid().equals(openid)) {
                storeService.updateOwnerInfo(store, null, null, null);
            }
        }

        upsertEmployee(reg, name, phone, role, store.getStoreName());
        upsertStoreContact(storeId, store.getStoreName(), name, phone);

        reg.setName(name);
        reg.setPhone(phone);
        reg.setRole(role);
        reg.setStatus("已绑定");
        registrationMapper.updateById(reg);

        if (removeDuplicates) {
            registrationMapper.delete(new LambdaQueryWrapper<OwnerRegistration>()
                    .eq(OwnerRegistration::getStoreId, storeId)
                    .eq(OwnerRegistration::getOpenid, openid)
                    .eq(OwnerRegistration::getStatus, "未关联")
                    .ne(OwnerRegistration::getId, reg.getId()));
        }
    }

    private void upsertEmployee(OwnerRegistration reg, String name, String phone, String role, String storeName) {
        Employee emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStoreId, reg.getStoreId())
                .eq(Employee::getOpenid, reg.getOpenid())
                .last("LIMIT 1"));
        if (emp == null) {
            emp = new Employee();
            emp.setEmployeeId("TMP");
            emp.setStoreId(reg.getStoreId());
            emp.setStoreName(storeName);
            emp.setStatus("在职");
            emp.setEmploymentType("全职");
            emp.setEntryDate(LocalDate.now());
        }
        emp.setOpenid(reg.getOpenid());
        emp.setName(name);
        emp.setMobile(phone);
        emp.setRole(role);
        emp.setStoreName(storeName);
        emp.setDelFlag(0);
        if (emp.getEntryDate() == null) {
            emp.setEntryDate(LocalDate.now());
        }
        if (emp.getId() == null) {
            employeeMapper.insert(emp);
            emp.setEmployeeId("EMP" + String.format("%08d", emp.getId()));
            employeeMapper.updateById(emp);
        } else {
            employeeMapper.updateById(emp);
        }
    }

    private void upsertStoreContact(String storeId, String storeName, String name, String phone) {
        StoreContact contact = contactMapper.selectOne(new LambdaQueryWrapper<StoreContact>()
                .eq(StoreContact::getStoreId, storeId)
                .eq(StoreContact::getContactName, name)
                .eq(StoreContact::getContactPhone, phone)
                .last("LIMIT 1"));
        if (contact == null) {
            contact = new StoreContact();
            contact.setStoreId(storeId);
            contact.setStoreName(storeName);
            contact.setContactName(name);
            contact.setContactPhone(phone);
            contact.setDelFlag(0);
            contactMapper.insert(contact);
        } else {
            contact.setStoreName(storeName);
            contact.setContactName(name);
            contact.setContactPhone(phone);
            contact.setDelFlag(0);
            contactMapper.updateById(contact);
        }
    }
}
