package com.xzcpc.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 物料基本信息变更事件。
 * 当物料名称、规格、单位等基本信息被修改时发布，用于同步更新引用该物料的未开始任务快照。
 */
@Getter
public class MaterialChangedEvent extends ApplicationEvent {
    private final String materialId;

    public MaterialChangedEvent(Object source, String materialId) {
        super(source);
        this.materialId = materialId;
    }
}
