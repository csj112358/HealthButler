package com.healthbutler.dto;

import lombok.Data;

import java.util.List;

@Data
public class DiagnosisRequest {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 基于哪些报告ID生成诊断
     */
    private List<Long> reportIds;
}