package com.xzcpc.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.common.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    /** 手写分页，不走 MyBatis-Plus 分页插件，彻底跳过 COUNT(*) */
    @Select("<script>" +
            "SELECT * FROM operation_log WHERE 1=1" +
            "<if test='source != null and source != \"\"'> AND source = #{source}</if>" +
            "<if test='username != null and username != \"\"'> AND username = #{username}</if>" +
            "<if test='module != null and module != \"\"'> AND module = #{module}</if>" +
            "<if test='operation != null and operation != \"\"'> AND operation = #{operation}</if>" +
            " ORDER BY created_at DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<OperationLog> selectPageRaw(@Param("source") String source,
                                     @Param("username") String username,
                                     @Param("module") String module,
                                     @Param("operation") String operation,
                                     @Param("offset") long offset,
                                     @Param("size") long size);
}
