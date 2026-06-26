package com.xzcpc.mp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.mp.entity.OwnerRegistration;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository("mpOwnerRegistrationMapper")
public interface OwnerRegistrationMapper extends BaseMapper<OwnerRegistration> {}
