package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("loss_standard")
public class LossStandard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String materialId;
    private String materialName;
    private String materialCategory;
    private String standardType;       // fruit_check / video_upload
    private String title;
    private String description;
    private String mediaUrls;           // 逗号分隔的图片/视频URL
    private Integer sortNo;
    private String storeId;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
    @Version
    private Integer version;
}
