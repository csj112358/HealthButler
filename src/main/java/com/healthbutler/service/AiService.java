package com.healthbutler.service;

import com.healthbutler.vo.OcrResult;

import java.util.List;
import java.util.Map;

public interface AiService {

    /**
     * 调用多模态大模型进行OCR识别
     * @param imageUrl 图片URL
     * @return OCR识别结果
     */
    OcrResult extractReportData(String imageUrl);

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
}