package com.healthbutler.service;

import com.healthbutler.vo.OcrResult;

import java.util.List;
import java.util.Map;

public interface AiService {

    /**
     * 调用多模态视觉大模型直接识别图片中的体检数据
     * @param imageBytes 图片原始字节
     * @return 识别结果
     */
    OcrResult extractReportData(byte[] imageBytes);

    /**
     * 调用文本大模型解析体检报告文字
     * @param text 用户粘贴的体检报告文字内容
     * @return OCR识别结果（结构相同）
     */
    OcrResult extractFromText(String text);

    /**
     * 调用文本大模型生成健康诊断
     * @param historicalData 历年体检数据
     * @param latestReport 最新报告数据
     * @return 诊断内容（Markdown格式）
     */
    String generateDiagnosis(List<Map<String, Object>> historicalData, Map<String, Object> latestReport);

    /**
     * 调用AI扫描重复指标，找出语义相同但名称不同的指标组
     * @param indicatorNames 所有指标名称列表
     * @return AI返回的JSON字符串（包含合并建议组）
     */
    String findDuplicateIndicators(List<String> indicatorNames);

    /**
     * 调用AI为缺少参考范围的指标建议参考范围
     * @param indicators 指标列表，每项包含 name, category, unit
     * @return AI返回的JSON字符串（数组，每项包含 name, suggestedRange, reason）
     */
    String suggestReferenceRanges(List<Map<String, String>> indicators);
}