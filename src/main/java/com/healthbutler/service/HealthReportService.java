package com.healthbutler.service;

import com.healthbutler.entity.HealthReport;
import com.healthbutler.vo.DiagnosisResult;
import com.healthbutler.vo.TrendData;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface HealthReportService {

    /**
     * 上传体检报告图片并进行OCR识别
     * @param userId 用户ID
     * @param file 体检报告图片
     * @return 识别后的报告ID
     */
    Long uploadAndExtract(Long userId, MultipartFile file);

    /**
     * 通过文字内容创建体检报告
     * @param userId 用户ID
     * @param text 体检报告文字内容
     * @return 包含报告ID和OCR结果的Map
     */
    Map<String, Object> createFromText(Long userId, String text);

    /**
     * 删除报告（逻辑删除）
     * @param reportId 报告ID
     */
    void deleteReport(Long reportId);

    /**
     * 确认报告数据（用户审核后）
     * @param reportId 报告ID
     * @param editedData 编辑后的数据（可选，含 reportDate、hospital、items）
     */
    void confirmReport(Long reportId, Map<String, Object> editedData);

    /**
     * 更新报告数据（编辑指标明细、日期、医院等）
     * @param reportId 报告ID
     * @param editedData 编辑后的数据
     */
    void updateReport(Long reportId, Map<String, Object> editedData);

    /**
     * 获取用户所有报告列表
     * @param userId 用户ID
     * @return 报告列表
     */
    List<HealthReport> getUserReports(Long userId);

    /**
     * 获取单个报告详情（包含指标明细）
     * @param reportId 报告ID
     * @return 报告详情（包含items列表）
     */
    Map<String, Object> getReportDetail(Long reportId);

    /**
     * 获取指标趋势数据（用于ECharts展示）
     * @param userId 用户ID
     * @param standardName 标准指标名称
     * @return 趋势数据
     */
    TrendData getTrendData(Long userId, String standardName);

    /**
     * 获取同类指标趋势数据
     * @param userId 用户ID
     * @param category 指标分类
     * @return 同类指标趋势数据列表
     */
    List<TrendData> getCategoryTrendData(Long userId, String category);

    /**
     * 生成AI健康诊断
     * @param userId 用户ID
     * @param reportIds 基于哪些报告生成诊断
     * @return 诊断结果
     */
    DiagnosisResult generateDiagnosis(Long userId, List<Long> reportIds);

    /**
     * 获取用户所有有数据的指标列表
     * @param userId 用户ID
     * @return 指标列表（包含名称、分类、最新值、状态等）
     */
    List<Map<String, Object>> getUserIndicators(Long userId);

    /**
     * 合并多个指标为同一个标准指标
     * @param userId 用户ID
     * @param standardNames 要合并的当前指标名称列表
     * @param newStandardName 合并后的标准名称
     * @param category 指标分类
     * @param unit 单位
     * @param referenceRange 参考范围
     */
    void mergeItems(Long userId, List<String> standardNames, String newStandardName, String category, String unit, String referenceRange);

    /**
     * 导出用户全部数据
     * @param userId 用户ID
     * @return 包含用户、报告、指标、字典、诊断的完整数据
     */
    Map<String, Object> exportAllData(Long userId);

    /**
     * AI扫描重复指标，返回候选合并组
     * @param userId 用户ID
     * @return AI识别的候选合并组列表
     */
    List<Map<String, Object>> scanDuplicateIndicators(Long userId);

    /**
     * 更新指标在字典中的参考范围（同时更新所有关联的 health_item）
     * @param standardName 标准指标名
     * @param referenceRange 新的参考范围
     */
    void updateReferenceRange(String standardName, String referenceRange);
}