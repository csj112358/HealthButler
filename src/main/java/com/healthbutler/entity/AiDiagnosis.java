package com.healthbutler.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_diagnosis")
public class AiDiagnosis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 诊断时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime diagnosisDate;

    /**
     * 基于哪些报告生成的诊断（JSON数组）
     */
    private String reportIds;

    /**
     * AI诊断内容（Markdown格式）
     */
    private String diagnosisContent;

    /**
     * 使用的AI模型名称
     */
    private String modelName;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}