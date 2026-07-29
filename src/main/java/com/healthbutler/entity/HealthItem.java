package com.healthbutler.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("health_item")
public class HealthItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reportId;

    private Long userId;

    /**
     * 关联的字典ID（标准化后的指标）
     */
    private Long dictionaryId;

    /**
     * 原始指标名称（医院报告中的名称）
     */
    private String originalName;

    /**
     * 标准化后的指标名称
     */
    private String standardName;

    /**
     * 指标分类
     */
    private String category;

    /**
     * 检测值（数值型）
     */
    private BigDecimal value;

    /**
     * 检测值（文本型，用于非数值结果）
     */
    private String textValue;

    /**
     * 单位
     */
    private String unit;

    /**
     * 参考范围
     */
    private String referenceRange;

    /**
     * 状态：normal-正常，high-偏高，low-偏低
     */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}