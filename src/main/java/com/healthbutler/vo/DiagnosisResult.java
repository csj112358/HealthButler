package com.healthbutler.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DiagnosisResult {

    /**
     * 诊断ID
     */
    private Long id;

    /**
     * 诊断时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime diagnosisDate;

    /**
     * 基于哪些报告生成的诊断
     */
    private List<Long> reportIds;

    /**
     * AI诊断内容（Markdown格式）
     */
    private String diagnosisContent;

    /**
     * 使用的AI模型名称
     */
    private String modelName;
}