package com.healthbutler.dto;

import lombok.Data;

@Data
public class OcrRequest {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 图片URL（如果已经上传到OSS）
     */
    private String imageUrl;
}