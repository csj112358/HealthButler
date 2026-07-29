package com.healthbutler.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.healthbutler.entity.AiDiagnosis;
import com.healthbutler.entity.HealthItem;
import com.healthbutler.entity.HealthReport;
import com.healthbutler.entity.ItemDictionary;
import com.healthbutler.mapper.AiDiagnosisMapper;
import com.healthbutler.mapper.HealthItemMapper;
import com.healthbutler.mapper.HealthReportMapper;
import com.healthbutler.mapper.ItemDictionaryMapper;
import com.healthbutler.service.AiService;
import com.healthbutler.service.HealthReportService;
import com.healthbutler.service.OssService;
import com.healthbutler.vo.DiagnosisResult;
import com.healthbutler.vo.OcrResult;
import com.healthbutler.vo.TrendData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HealthReportServiceImpl implements HealthReportService {

    @Autowired
    private AiService aiService;

    @Autowired
    private OssService ossService;

    @Autowired
    private HealthReportMapper healthReportMapper;

    @Autowired
    private HealthItemMapper healthItemMapper;

    @Autowired
    private ItemDictionaryMapper itemDictionaryMapper;

    @Autowired
    private AiDiagnosisMapper aiDiagnosisMapper;

    @Override
    @Transactional
    @CacheEvict(value = {"userReports", "trendData", "categoryTrendData"}, allEntries = true)
    public Long uploadAndExtract(Long userId, MultipartFile file) {
        // 1. 上传图片到OSS（这里简化处理，实际应该上传到对象存储）
        String imageUrl = uploadImage(file);

        // 2. 调用AI进行OCR识别
        OcrResult ocrResult = aiService.extractReportData(imageUrl);

        // 3. 保存报告基本信息
        HealthReport report = new HealthReport();
        report.setUserId(userId);

        String reportDate = ocrResult.getReportDate();
        if (reportDate != null && !reportDate.trim().isEmpty()) {
            report.setReportDate(java.time.LocalDate.parse(reportDate.trim()));
        } else {
            report.setReportDate(java.time.LocalDate.now());
        }

        report.setHospital(ocrResult.getHospital() != null ? ocrResult.getHospital() : "未知医院");
        report.setImageUrl(imageUrl);
        report.setAiRawResponse(JSON.toJSONString(ocrResult));
        report.setStatus(0); // 待审核
        healthReportMapper.insert(report);

        // 4. 保存指标明细
        if (ocrResult.getItems() != null && !ocrResult.getItems().isEmpty()) {
            saveHealthItems(report.getId(), userId, ocrResult.getItems());
        }

        return report.getId();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"userReports", "trendData", "categoryTrendData"}, allEntries = true)
    public Map<String, Object> createFromText(Long userId, String text) {
        // 1. 调用AI解析文字内容
        OcrResult ocrResult = aiService.extractFromText(text);

        // 2. 保存报告基本信息（无图片）
        HealthReport report = new HealthReport();
        report.setUserId(userId);

        // 解析日期，如果AI未返回则使用今天
        String reportDate = ocrResult.getReportDate();
        if (reportDate != null && !reportDate.trim().isEmpty()) {
            report.setReportDate(java.time.LocalDate.parse(reportDate.trim()));
        } else {
            report.setReportDate(java.time.LocalDate.now());
        }

        report.setHospital(ocrResult.getHospital() != null ? ocrResult.getHospital() : "未知医院");
        report.setImageUrl(null);
        report.setAiRawResponse(JSON.toJSONString(ocrResult));
        report.setStatus(0); // 待审核
        healthReportMapper.insert(report);

        // 3. 保存指标明细
        if (ocrResult.getItems() != null && !ocrResult.getItems().isEmpty()) {
            saveHealthItems(report.getId(), userId, ocrResult.getItems());
        }

        // 4. 返回结果（包含报告ID和OCR结果）
        Map<String, Object> result = new HashMap<>();
        result.put("reportId", report.getId());
        result.put("ocrResult", ocrResult);
        return result;
    }

    @Override
    @Transactional
    @CacheEvict(value = {"userReports", "reportDetail"}, allEntries = true)
    public void confirmReport(Long reportId, Map<String, Object> editedData) {
        HealthReport report = healthReportMapper.selectById(reportId);
        if (report == null) {
            throw new RuntimeException("报告不存在");
        }

        // 如果用户编辑了数据，先更新报告和指标
        if (editedData != null && !editedData.isEmpty()) {
            // 更新报告日期
            if (editedData.containsKey("reportDate")) {
                String dateStr = (String) editedData.get("reportDate");
                if (dateStr != null && !dateStr.trim().isEmpty()) {
                    report.setReportDate(java.time.LocalDate.parse(dateStr.trim()));
                }
            }
            // 更新医院名称
            if (editedData.containsKey("hospital")) {
                String hospital = (String) editedData.get("hospital");
                if (hospital != null && !hospital.trim().isEmpty()) {
                    report.setHospital(hospital.trim());
                }
            }
            healthReportMapper.updateById(report);

            // 更新指标明细：删除旧的，插入新的
            if (editedData.containsKey("items")) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) editedData.get("items");
                if (items != null) {
                    // 删除旧指标
                    healthItemMapper.delete(
                            new LambdaQueryWrapper<HealthItem>().eq(HealthItem::getReportId, reportId)
                    );
                    // 插入编辑后的指标
                    for (Map<String, Object> item : items) {
                        HealthItem healthItem = new HealthItem();
                        healthItem.setReportId(reportId);
                        healthItem.setUserId(report.getUserId());
                        healthItem.setOriginalName((String) item.get("name"));
                        healthItem.setCategory((String) item.get("category"));

                        Object valueObj = item.get("value");
                        if (valueObj != null) {
                            healthItem.setValue(new java.math.BigDecimal(valueObj.toString()));
                        }

                        healthItem.setUnit((String) item.get("unit"));
                        healthItem.setReferenceRange((String) item.get("referenceRange"));

                        // 尝试匹配字典
                        ItemDictionary dictionary = matchDictionary((String) item.get("name"));
                        if (dictionary != null) {
                            healthItem.setDictionaryId(dictionary.getId());
                            healthItem.setStandardName(dictionary.getStandardName());
                            healthItem.setReferenceRange(dictionary.getReferenceRange());
                            if (valueObj != null) {
                                healthItem.setStatus(determineStatus(
                                        Double.parseDouble(valueObj.toString()), dictionary.getReferenceRange()));
                            } else {
                                healthItem.setStatus("normal");
                            }
                        } else {
                            healthItem.setStandardName((String) item.get("name"));
                            healthItem.setStatus(normalizeStatus((String) item.get("status")));
                        }

                        healthItemMapper.insert(healthItem);
                    }
                }
            }
        }

        report.setStatus(1); // 已确认
        healthReportMapper.updateById(report);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"userReports", "reportDetail", "trendData", "categoryTrendData"}, allEntries = true)
    public void updateReport(Long reportId, Map<String, Object> editedData) {
        HealthReport report = healthReportMapper.selectById(reportId);
        if (report == null) {
            throw new RuntimeException("报告不存在");
        }

        // 更新报告基本信息
        if (editedData.containsKey("reportDate")) {
            String dateStr = (String) editedData.get("reportDate");
            if (dateStr != null && !dateStr.trim().isEmpty()) {
                report.setReportDate(java.time.LocalDate.parse(dateStr.trim()));
            }
        }
        if (editedData.containsKey("hospital")) {
            String hospital = (String) editedData.get("hospital");
            if (hospital != null && !hospital.trim().isEmpty()) {
                report.setHospital(hospital.trim());
            }
        }
        healthReportMapper.updateById(report);

        // 更新指标明细：删除旧的，插入新的
        if (editedData.containsKey("items")) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) editedData.get("items");
            if (items != null) {
                healthItemMapper.delete(
                        new LambdaQueryWrapper<HealthItem>().eq(HealthItem::getReportId, reportId)
                );
                for (Map<String, Object> item : items) {
                    HealthItem healthItem = new HealthItem();
                    healthItem.setReportId(reportId);
                    healthItem.setUserId(report.getUserId());
                    healthItem.setOriginalName((String) item.get("name"));
                    healthItem.setCategory((String) item.get("category"));

                    Object valueObj = item.get("value");
                    if (valueObj != null) {
                        healthItem.setValue(new java.math.BigDecimal(valueObj.toString()));
                    }

                    healthItem.setUnit((String) item.get("unit"));
                    healthItem.setReferenceRange((String) item.get("referenceRange"));

                    ItemDictionary dictionary = matchDictionary((String) item.get("name"));
                    if (dictionary != null) {
                        healthItem.setDictionaryId(dictionary.getId());
                        healthItem.setStandardName(dictionary.getStandardName());
                        healthItem.setReferenceRange(dictionary.getReferenceRange());
                        if (valueObj != null) {
                            healthItem.setStatus(determineStatus(
                                    Double.parseDouble(valueObj.toString()), dictionary.getReferenceRange()));
                        } else {
                            healthItem.setStatus("normal");
                        }
                    } else {
                        healthItem.setStandardName((String) item.get("name"));
                        healthItem.setStatus(normalizeStatus((String) item.get("status")));
                    }

                    healthItemMapper.insert(healthItem);
                }
            }
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"userReports", "reportDetail", "trendData", "categoryTrendData"}, allEntries = true)
    public void deleteReport(Long reportId) {
        HealthReport report = healthReportMapper.selectById(reportId);
        if (report == null) {
            throw new RuntimeException("报告不存在");
        }
        // 先删除关联的指标明细
        healthItemMapper.delete(
                new LambdaQueryWrapper<HealthItem>().eq(HealthItem::getReportId, reportId)
        );
        // 逻辑删除报告
        healthReportMapper.deleteById(reportId);
    }

    @Override
    @Cacheable(value = "userReports", key = "#userId")
    public List<HealthReport> getUserReports(Long userId) {
        return healthReportMapper.selectList(
                new LambdaQueryWrapper<HealthReport>()
                        .eq(HealthReport::getUserId, userId)
                        .orderByDesc(HealthReport::getReportDate)
        );
    }

    @Override
    @Cacheable(value = "reportDetail", key = "#reportId")
    public Map<String, Object> getReportDetail(Long reportId) {
        HealthReport report = healthReportMapper.selectById(reportId);
        if (report == null) {
            throw new RuntimeException("报告不存在");
        }
        // 查询关联的指标明细
        List<HealthItem> items = healthItemMapper.selectList(
                new LambdaQueryWrapper<HealthItem>().eq(HealthItem::getReportId, reportId)
        );
        Map<String, Object> result = new HashMap<>();
        result.put("id", report.getId());
        result.put("userId", report.getUserId());
        result.put("reportDate", report.getReportDate());
        result.put("hospital", report.getHospital());
        result.put("reportType", report.getReportType());
        result.put("imageUrl", report.getImageUrl());
        result.put("status", report.getStatus());
        result.put("createTime", report.getCreateTime());
        result.put("items", items);
        return result;
    }

    @Override
    @Cacheable(value = "trendData", key = "#userId + ':' + #standardName")
    public TrendData getTrendData(Long userId, String standardName) {
        // 查询该用户所有该指标的历史数据
        List<HealthItem> items = healthItemMapper.selectList(
                new LambdaQueryWrapper<HealthItem>()
                        .eq(HealthItem::getUserId, userId)
                        .eq(HealthItem::getStandardName, standardName)
        );

        if (items.isEmpty()) {
            return null;
        }

        // 获取指标字典信息
        ItemDictionary dictionary = getDictionaryByName(standardName);

        // 构建趋势数据，按体检日期排序
        List<TrendData.DataPoint> dataPoints = new ArrayList<>();
        for (HealthItem item : items) {
            HealthReport report = healthReportMapper.selectById(item.getReportId());
            if (report == null) continue;

            TrendData.DataPoint point = new TrendData.DataPoint();
            point.setDate(report.getReportDate().toString());
            point.setValue(item.getValue());
            point.setStatus(item.getStatus());
            dataPoints.add(point);
        }

        // 按日期升序排列
        dataPoints.sort(Comparator.comparing(TrendData.DataPoint::getDate));

        TrendData trendData = new TrendData();
        trendData.setStandardName(standardName);
        trendData.setCategory(dictionary != null ? dictionary.getCategory() : items.get(0).getCategory());
        trendData.setUnit(dictionary != null ? dictionary.getUnit() : items.get(0).getUnit());
        trendData.setReferenceRange(dictionary != null ? dictionary.getReferenceRange() : items.get(0).getReferenceRange());
        trendData.setDataPoints(dataPoints);
        return trendData;
    }

    @Override
    @Cacheable(value = "categoryTrendData", key = "#userId + ':' + #category")
    public List<TrendData> getCategoryTrendData(Long userId, String category) {
        // 查询该分类下所有指标
        List<ItemDictionary> dictionaries = itemDictionaryMapper.selectList(
                new LambdaQueryWrapper<ItemDictionary>()
                        .eq(ItemDictionary::getCategory, category)
        );

        List<TrendData> trendDataList = new ArrayList<>();
        for (ItemDictionary dictionary : dictionaries) {
            TrendData trendData = getTrendData(userId, dictionary.getStandardName());
            if (trendData != null) {
                trendDataList.add(trendData);
            }
        }

        return trendDataList;
    }

    @Override
    @Transactional
    public DiagnosisResult generateDiagnosis(Long userId, List<Long> reportIds) {
        // 1. 获取历史报告数据
        List<Map<String, Object>> historicalData = new ArrayList<>();
        Map<String, Object> latestReport = null;

        for (Long reportId : reportIds) {
            HealthReport report = healthReportMapper.selectById(reportId);
            if (report == null) continue;

            // 获取报告的指标明细
            List<HealthItem> items = healthItemMapper.selectList(
                    new LambdaQueryWrapper<HealthItem>()
                            .eq(HealthItem::getReportId, reportId)
            );

            Map<String, Object> reportData = new HashMap<>();
            reportData.put("report_date", report.getReportDate().toString());
            reportData.put("hospital", report.getHospital());
            reportData.put("items", items.stream().map(item -> {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("standard_name", item.getStandardName());
                itemMap.put("value", item.getValue());
                itemMap.put("unit", item.getUnit());
                itemMap.put("reference_range", item.getReferenceRange());
                itemMap.put("status", item.getStatus());
                return itemMap;
            }).collect(Collectors.toList()));

            historicalData.add(reportData);

            // 最后一个报告作为最新报告
            latestReport = reportData;
        }

        // 2. 调用AI生成诊断
        String diagnosisContent = aiService.generateDiagnosis(historicalData, latestReport);

        // 3. 保存诊断结果
        AiDiagnosis diagnosis = new AiDiagnosis();
        diagnosis.setUserId(userId);
        diagnosis.setDiagnosisDate(LocalDateTime.now());
        diagnosis.setReportIds(JSON.toJSONString(reportIds));
        diagnosis.setDiagnosisContent(diagnosisContent);
        diagnosis.setModelName("health-ai");
        aiDiagnosisMapper.insert(diagnosis);

        // 4. 返回结果
        DiagnosisResult result = new DiagnosisResult();
        result.setId(diagnosis.getId());
        result.setDiagnosisDate(diagnosis.getDiagnosisDate());
        result.setReportIds(reportIds);
        result.setDiagnosisContent(diagnosisContent);
        result.setModelName(diagnosis.getModelName());

        return result;
    }

    /**
     * 上传图片到存储服务
     */
    private String uploadImage(MultipartFile file) {
        return ossService.uploadFile(file, "health-reports");
    }

    /**
     * 保存指标明细
     */
    private void saveHealthItems(Long reportId, Long userId, List<OcrResult.Item> items) {
        for (OcrResult.Item item : items) {
            HealthItem healthItem = new HealthItem();
            healthItem.setReportId(reportId);
            healthItem.setUserId(userId);
            healthItem.setOriginalName(item.getName());
            healthItem.setCategory(item.getCategory());
            healthItem.setValue(BigDecimal.valueOf(item.getValue()));
            healthItem.setUnit(item.getUnit());

            // 尝试匹配指标字典，获取标准化名称和参考区间
            ItemDictionary dictionary = matchDictionary(item.getName());
            if (dictionary != null) {
                healthItem.setDictionaryId(dictionary.getId());
                healthItem.setStandardName(dictionary.getStandardName());
                // 使用字典中的参考区间
                healthItem.setReferenceRange(dictionary.getReferenceRange());
                // 根据参考区间判断状态（优先使用字典，不依赖AI）
                healthItem.setStatus(determineStatus(item.getValue(), dictionary.getReferenceRange()));
            } else {
                healthItem.setStandardName(item.getName());
                healthItem.setReferenceRange(item.getReferenceRange());
                // 没有字典时使用AI返回的状态
                healthItem.setStatus(normalizeStatus(item.getStatus()));
            }

            healthItemMapper.insert(healthItem);
        }
    }

    /**
     * 根据参考区间判断状态
     * @param value 检测值
     * @param referenceRange 参考区间（如 "9-50", "≤23.0", "<3.37"）
     * @return normal/high/low
     */
    private String determineStatus(Double value, String referenceRange) {
        if (referenceRange == null || referenceRange.isEmpty()) return "normal";
        String range = referenceRange.trim();

        try {
            // 处理 "≤X" 格式
            if (range.startsWith("≤") || range.startsWith("<=")) {
                double max = Double.parseDouble(range.replace("≤", "").replace("<=", "").trim());
                return value <= max ? "normal" : "high";
            }
            // 处理 "≥X" 格式
            if (range.startsWith("≥") || range.startsWith(">=")) {
                double min = Double.parseDouble(range.replace("≥", "").replace(">=", "").trim());
                return value >= min ? "normal" : "low";
            }
            // 处理 "<X" 格式
            if (range.startsWith("<")) {
                double max = Double.parseDouble(range.substring(1).trim());
                return value < max ? "normal" : "high";
            }
            // 处理 ">X" 格式
            if (range.startsWith(">")) {
                double min = Double.parseDouble(range.substring(1).trim());
                return value > min ? "normal" : "low";
            }
            // 处理 "X-Y" 格式
            if (range.contains("-")) {
                String[] parts = range.split("-");
                double min = Double.parseDouble(parts[0].trim());
                double max = Double.parseDouble(parts[1].trim());
                if (value < min) return "low";
                if (value > max) return "high";
                return "normal";
            }
        } catch (NumberFormatException e) {
            // 解析失败时返回normal
            return "normal";
        }

        return "normal";
    }

    @Override
    public List<Map<String, Object>> getUserIndicators(Long userId) {
        // 查询用户所有指标
        List<HealthItem> allItems = healthItemMapper.selectList(
                new LambdaQueryWrapper<HealthItem>()
                        .eq(HealthItem::getUserId, userId)
        );

        // 按指标名称分组，保留体检日期最新的那条
        Map<String, HealthItem> latestMap = new java.util.LinkedHashMap<>();
        Map<Long, java.time.LocalDate> reportDateCache = new java.util.HashMap<>();
        for (HealthItem item : allItems) {
            String key = item.getStandardName() != null ? item.getStandardName() : item.getOriginalName();
            if (key == null) continue;

            // 获取该指标对应的体检日期（带缓存）
            java.time.LocalDate reportDate = reportDateCache.computeIfAbsent(item.getReportId(), rid -> {
                HealthReport r = healthReportMapper.selectById(rid);
                return r != null ? r.getReportDate() : java.time.LocalDate.MIN;
            });

            if (!latestMap.containsKey(key)) {
                latestMap.put(key, item);
            } else {
                // 比较体检日期，保留最新的
                HealthItem existing = latestMap.get(key);
                java.time.LocalDate existingDate = reportDateCache.computeIfAbsent(existing.getReportId(), rid -> {
                    HealthReport r = healthReportMapper.selectById(rid);
                    return r != null ? r.getReportDate() : java.time.LocalDate.MIN;
                });
                if (reportDate.isAfter(existingDate)) {
                    latestMap.put(key, item);
                }
            }
        }

        // 构建返回数据
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map.Entry<String, HealthItem> entry : latestMap.entrySet()) {
            HealthItem item = entry.getValue();
            Map<String, Object> indicator = new java.util.HashMap<>();
            indicator.put("name", entry.getKey());
            indicator.put("category", item.getCategory() != null ? item.getCategory() : "其他");
            indicator.put("value", item.getValue());
            indicator.put("unit", item.getUnit());
            indicator.put("referenceRange", item.getReferenceRange());
            indicator.put("status", item.getStatus());
            result.add(indicator);
        }

        return result;
    }

    /**
     * 匹配指标字典
     * 根据原始名称匹配标准指标名称
     */
    private ItemDictionary matchDictionary(String originalName) {
        // 查询所有字典
        List<ItemDictionary> dictionaries = itemDictionaryMapper.selectList(null);

        for (ItemDictionary dictionary : dictionaries) {
            // 检查是否完全匹配标准名称
            if (dictionary.getStandardName().equals(originalName)) {
                return dictionary;
            }

            // 检查是否匹配别名
            if (dictionary.getAliases() != null) {
                JSONArray aliases = JSON.parseArray(dictionary.getAliases());
                for (int i = 0; i < aliases.size(); i++) {
                    if (aliases.getString(i).equals(originalName)) {
                        return dictionary;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 根据标准名称获取字典
     */
    private ItemDictionary getDictionaryByName(String standardName) {
        return itemDictionaryMapper.selectOne(
                new LambdaQueryWrapper<ItemDictionary>()
                        .eq(ItemDictionary::getStandardName, standardName)
        );
    }

    /**
     * 标准化状态值
     * 将AI返回的各种状态格式统一为 normal/high/low
     */
    private String normalizeStatus(String status) {
        if (status == null) return "normal";
        String s = status.trim().toLowerCase();
        if (s.equals("normal") || s.equals("正常") || s.equals("正常值")) return "normal";
        if (s.equals("high") || s.equals("偏高") || s.equals("升高") || s.equals("高") || s.equals("↑")) return "high";
        if (s.equals("low") || s.equals("偏低") || s.equals("降低") || s.equals("低") || s.equals("↓")) return "low";
        return "normal";
    }
}