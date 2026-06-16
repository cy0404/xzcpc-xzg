package com.xzcpc.common.model;

import lombok.Data;

@Data
public class MaterialInfo {
    private String id;
    private String pinxiangbianma;
    private String leibie;      // 父级分类
    private String leibie2;     // 分类
    private String yuancailiaomingcheng;
    private String guige;
    private String pandiandanwei;
}
