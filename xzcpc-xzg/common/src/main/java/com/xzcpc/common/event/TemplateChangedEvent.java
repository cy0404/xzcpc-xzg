package com.xzcpc.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TemplateChangedEvent extends ApplicationEvent {
    private final Integer templateId;

    public TemplateChangedEvent(Object source, Integer templateId) {
        super(source);
        this.templateId = templateId;
    }
}
