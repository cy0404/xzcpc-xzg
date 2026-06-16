package com.xzcpc.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解，标注在 Controller 方法上自动记录操作日志。
 * 使用示例：{@code @OpLog(module = "物料", operation = "新增", desc = "可乐")}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpLog {

    /** 模块：物料/模板/任务 */
    String module();

    /** 操作：新增/编辑/删除/启停/提交 */
    String operation();

    /** 操作描述，如 "物料ID=#materialId" */
    String desc() default "";
}
