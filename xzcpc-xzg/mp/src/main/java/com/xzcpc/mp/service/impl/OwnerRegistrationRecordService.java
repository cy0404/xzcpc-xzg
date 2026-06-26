package com.xzcpc.mp.service.impl;

import com.xzcpc.mp.entity.OwnerRegistration;
import com.xzcpc.mp.mapper.OwnerRegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnerRegistrationRecordService {

    private final OwnerRegistrationMapper registrationMapper;

    /** 独立事务写入，后续绑定失败也不回滚登记记录 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public OwnerRegistration saveAttempt(OwnerRegistration reg) {
        registrationMapper.insert(reg);
        return reg;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        OwnerRegistration update = new OwnerRegistration();
        update.setId(id);
        update.setStatus(status);
        registrationMapper.updateById(update);
    }
}
