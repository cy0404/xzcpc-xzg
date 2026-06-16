package com.xzcpc.mp.dto;

import lombok.Data;
import java.util.List;

@Data
public class SaveZoneReq {
    private List<ZoneMaterialItem> items;
}
