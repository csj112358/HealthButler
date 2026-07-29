package com.healthbutler.vo;

import lombok.Data;

import java.util.List;

@Data
public class OcrResult {

    /**
     * 体检日期
     */
    private String reportDate;

    /**
     * 医院名称
     */
    private String hospital;

    /**
     * 检测项目列表
     */
    private List<Item> items;

    @Data
    public static class Item {

        /**
         * 指标名称
         */
        private String name;

        /**
         * 所属分类（如肝功能）
         */
        private String category;

        /**
         * 检测值（浮点数）
         */
        private Double value;

        /**
         * 单位
         */
        private String unit;

        /**
         * 参考范围
         */
        private String referenceRange;

        /**
         * 状态：normal/high/low
         */
        private String status;
    }
}