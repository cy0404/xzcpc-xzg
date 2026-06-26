package com.xzcpc.mp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.mp.entity.StoreContact;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository("mpStoreContactMapper")
public interface StoreContactMapper extends BaseMapper<StoreContact> {}
