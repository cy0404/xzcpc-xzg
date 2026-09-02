package com.xzcpc.mp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.mp.entity.SupervisorStoreAccess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SupervisorStoreAccessMapper extends BaseMapper<SupervisorStoreAccess> {

    /**
     * 按 open_id + store_id 查任意一行（含逻辑删除行）。
     * 用于 upsert 撞物理唯一键 uk_openid_store 的场景：
     * 收敛软删后同一 (openId, storeId) 再出现时，需复用软删行（复活）而不是 INSERT 撞键。
     */
    @Select("SELECT id, open_id, admin_name, store_id, store_name, source, created_at, del_flag " +
            "FROM supervisor_store_access WHERE open_id = #{openId} AND store_id = #{storeId} LIMIT 1")
    SupervisorStoreAccess selectAnyByOpenIdAndStoreId(@Param("openId") String openId,
                                                      @Param("storeId") String storeId);
}
