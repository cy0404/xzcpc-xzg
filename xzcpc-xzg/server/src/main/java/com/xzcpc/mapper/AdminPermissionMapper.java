package com.xzcpc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.entity.AdminPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminPermissionMapper extends BaseMapper<AdminPermission> {

    /** 绕过 @TableLogic 的 del_flag 过滤，按 openId 直接查 */
    @Select("SELECT * FROM admin_permission WHERE open_id = #{openId} LIMIT 1")
    AdminPermission selectByOpenIdRaw(String openId);
}
