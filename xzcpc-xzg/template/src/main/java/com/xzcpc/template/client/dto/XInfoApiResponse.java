package com.xzcpc.template.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * xinfo API 统一响应信封：{ "items": [...] }
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class XInfoApiResponse<T> {

    private List<T> items;
}
