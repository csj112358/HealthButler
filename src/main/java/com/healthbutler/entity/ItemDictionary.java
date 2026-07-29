package com.healthbutler.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("item_dictionary")
public class ItemDictionary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 标准指标名称
     */
    private String standardName;

    /**
     * 指标分类（如：肝功能、肾功能、血脂、血糖等）
     */
    private String category;

    /**
     * 别名列表（JSON数组，存储各医院可能使用的不同名称）
     */
    private String aliases;

    /**
     * 标准单位
     */
    private String unit;

    /**
     * 参考范围（通用）
     */
    private String referenceRange;

    /**
     * 指标说明
     */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}