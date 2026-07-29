package com.healthbutler.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TrendData {

    /**
     * 指标名称
     */
    private String standardName;

    /**
     * 指标分类
     */
    private String category;

    /**
     * 单位
     */
    private String unit;

    /**
     * 参考范围
     */
    private String referenceRange;

    /**
     * 历年数据点
     */
    private List<DataPoint> dataPoints;

    @Data
    public static class DataPoint {

        /**
         * 日期（YYYY-MM-DD格式）
         */
        private String date;

        /**
         * 检测值
         */
        private BigDecimal value;

        /**
         * 状态：normal/high/low
         */
        private String status;
    }
}