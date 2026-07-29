package com.healthbutler.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("health_report")
public class HealthReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 体检日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reportDate;

    /**
     * 体检医院
     */
    private String hospital;

    /**
     * 报告类型（如：年度体检、入职体检等）
     */
    private String reportType;

    /**
     * 原始报告图片URL
     */
    private String imageUrl;

    /**
     * AI原始返回结果（用于调试）
     */
    private String aiRawResponse;

    /**
     * 状态：0-待审核，1-已确认，2-已忽略
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}