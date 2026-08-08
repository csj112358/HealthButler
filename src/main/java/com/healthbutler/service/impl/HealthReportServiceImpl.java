package com.healthbutler.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.healthbutler.entity.AiDiagnosis;
import com.healthbutler.entity.HealthItem;
import com.healthbutler.entity.HealthReport;
import com.healthbutler.entity.ItemDictionary;
import com.healthbutler.entity.User;
import com.healthbutler.exception.BusinessException;
import com.healthbutler.mapper.AiDiagnosisMapper;
import com.healthbutler.mapper.HealthItemMapper;
import com.healthbutler.mapper.HealthReportMapper;
import com.healthbutler.mapper.ItemDictionaryMapper;
import com.healthbutler.service.AiService;
import com.healthbutler.service.HealthReportService;
import com.healthbutler.vo.DiagnosisResult;
import com.healthbutler.vo.OcrResult;
import com.healthbutler.vo.TrendData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HealthReportServiceImpl implements HealthReportService {

    @Autowired
    private AiService aiService;

    @Autowired
    private HealthReportMapper healthReportMapper;

    @Autowired
    private HealthItemMapper healthItemMapper;

    @Autowired
    private ItemDictionaryMapper itemDictionaryMapper;

    @Autowired
    private AiDiagnosisMapper aiDiagnosisMapper;

    @Autowired
    private com.healthbutler.mapper.UserMapper userMapper;

    @Override
    @Transactional
    @CacheEvict(value = {"userReports", "trendData", "categoryTrendData"}, allEntries = true)
    public Long uploadAndExtract(Long userId, MultipartFile file) {
        // 1. 读取文件字节
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(500, "读取上传文件失败", e);
        }

        // 2. 调用多模态视觉大模型进行识别
        OcrResult ocrResult = aiService.extractReportData(fileBytes);

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
        report.setImageUrl(null);
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
            throw new BusinessException(404, "报告不存在");
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
                            // 如果分类为空，用字典的分类填补
                            if (item.get("category") == null || ((String) item.get("category")).trim().isEmpty()) {
                                healthItem.setCategory(dictionary.getCategory());
                            }
                            if (valueObj != null) {
                                String status = determineStatus(
                                        Double.parseDouble(valueObj.toString()), dictionary.getReferenceRange());
                                healthItem.setStatus(status != null ? status : normalizeStatus((String) item.get("status")));
                            } else {
                                healthItem.setStatus("normal");
                            }
                        } else {
                            healthItem.setStandardName((String) item.get("name"));
                            String status = null;
                            if (valueObj != null) {
                                status = determineStatus(
                                        Double.parseDouble(valueObj.toString()),
                                        (String) item.get("referenceRange"));
                            }
                            healthItem.setStatus(status != null ? status : normalizeStatus((String) item.get("status")));
                        }

                        // 最终兜底：分类为空时设为"其他"
                        if (healthItem.getCategory() == null || healthItem.getCategory().trim().isEmpty()) {
                            healthItem.setCategory("其他");
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
            throw new BusinessException(404, "报告不存在");
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
                        // 如果分类为空，用字典的分类填补
                        if (item.get("category") == null || ((String) item.get("category")).trim().isEmpty()) {
                            healthItem.setCategory(dictionary.getCategory());
                        }
                        if (valueObj != null) {
                            String status = determineStatus(
                                    Double.parseDouble(valueObj.toString()), dictionary.getReferenceRange());
                            healthItem.setStatus(status != null ? status : normalizeStatus((String) item.get("status")));
                        } else {
                            healthItem.setStatus("normal");
                        }
                    } else {
                        healthItem.setStandardName((String) item.get("name"));
                        String status = null;
                        if (valueObj != null) {
                            status = determineStatus(
                                    Double.parseDouble(valueObj.toString()),
                                    (String) item.get("referenceRange"));
                        }
                        healthItem.setStatus(status != null ? status : normalizeStatus((String) item.get("status")));
                    }

                    // 最终兜底：分类为空时设为"其他"
                    if (healthItem.getCategory() == null || healthItem.getCategory().trim().isEmpty()) {
                        healthItem.setCategory("其他");
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
            throw new BusinessException(404, "报告不存在");
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
            throw new BusinessException(404, "报告不存在");
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
                healthItem.setReferenceRange(dictionary.getReferenceRange());
                // 如果 AI 没有返回分类，用字典的分类填补
                if (item.getCategory() == null || item.getCategory().trim().isEmpty()) {
                    healthItem.setCategory(dictionary.getCategory());
                }
                // 优先用程序根据参考区间计算状态
                String status = determineStatus(item.getValue(), dictionary.getReferenceRange());
                healthItem.setStatus(status != null ? status : normalizeStatus(item.getStatus()));
            } else {
                healthItem.setStandardName(item.getName());
                healthItem.setReferenceRange(item.getReferenceRange());
                // 先尝试用参考区间程序化判断，失败则降级使用AI返回的状态
                String status = determineStatus(item.getValue(), item.getReferenceRange());
                healthItem.setStatus(status != null ? status : normalizeStatus(item.getStatus()));
            }

            // 最终兜底：如果分类仍然为空，设为"其他"
            if (healthItem.getCategory() == null || healthItem.getCategory().trim().isEmpty()) {
                healthItem.setCategory("其他");
            }

            healthItemMapper.insert(healthItem);
        }
    }

    /**
     * 根据参考区间判断状态。
     * @param value 检测值
     * @param referenceRange 参考区间（如 "9-50", "≤23.0", "<3.37"）
     * @return normal/high/low，无法解析时返回 null 让调用方降级处理
     */
    private String determineStatus(Double value, String referenceRange) {
        if (referenceRange == null || referenceRange.isEmpty()) return null;
        String range = referenceRange.trim();

        try {
            // 处理 "≤X" 或 "<=X" 格式
            if (range.startsWith("≤") || range.startsWith("<=")) {
                double max = Double.parseDouble(range.replace("≤", "").replace("<=", "").trim());
                return value <= max ? "normal" : "high";
            }
            // 处理 "≥X" 或 ">=X" 格式
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
            // 处理 "X-Y" 或 "X~Y" 格式
            if (range.contains("-") || range.contains("~")) {
                String[] parts = range.split("[-~]");
                double min = Double.parseDouble(parts[0].trim());
                double max = Double.parseDouble(parts[1].trim());
                if (value < min) return "low";
                if (value > max) return "high";
                return "normal";
            }
        } catch (NumberFormatException e) {
            // 解析失败，返回 null 让调用方降级
            return null;
        }

        // 格式不匹配任何已知模式，返回 null
        return null;
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

    @Override
    @Transactional
    @CacheEvict(value = {"trendData", "categoryTrendData", "reportDetail", "userReports"}, allEntries = true)
    public void mergeItems(Long userId, List<String> standardNames, String newStandardName, String category, String unit, String referenceRange) {
        if (standardNames == null || standardNames.size() < 2) {
            throw new BusinessException(400, "请至少选择2个指标进行合并");
        }
        if (newStandardName == null || newStandardName.trim().isEmpty()) {
            throw new BusinessException(400, "合并后的指标名称不能为空");
        }

        // 1. 查询这些名称对应的所有指标记录
        List<HealthItem> allItems = healthItemMapper.selectList(
                new LambdaQueryWrapper<HealthItem>()
                        .eq(HealthItem::getUserId, userId)
                        .in(HealthItem::getStandardName, standardNames)
        );

        // 也查出 originalName 匹配的
        List<HealthItem> byOriginal = healthItemMapper.selectList(
                new LambdaQueryWrapper<HealthItem>()
                        .eq(HealthItem::getUserId, userId)
                        .in(HealthItem::getOriginalName, standardNames)
        );
        Set<Long> seenIds = new HashSet<>();
        for (HealthItem item : allItems) seenIds.add(item.getId());
        for (HealthItem item : byOriginal) {
            if (!seenIds.contains(item.getId())) {
                allItems.add(item);
            }
        }

        if (allItems.isEmpty()) {
            throw new BusinessException(400, "未找到指定的指标");
        }

        // 2. 收集所有原始名称作为别名
        Set<String> aliases = new LinkedHashSet<>();
        for (HealthItem item : allItems) {
            if (item.getOriginalName() != null && !item.getOriginalName().trim().isEmpty()) {
                aliases.add(item.getOriginalName().trim());
            }
        }
        // 被选中的标准名也加入别名
        aliases.addAll(standardNames);

        // 3. 查找或创建 item_dictionary 记录
        ItemDictionary dictionary = itemDictionaryMapper.selectOne(
                new LambdaQueryWrapper<ItemDictionary>()
                        .eq(ItemDictionary::getStandardName, newStandardName)
        );

        if (dictionary == null) {
            dictionary = new ItemDictionary();
            dictionary.setStandardName(newStandardName.trim());
            dictionary.setCategory(category != null ? category.trim() : "其他");
            dictionary.setUnit(unit != null ? unit.trim() : "");
            dictionary.setReferenceRange(referenceRange != null ? referenceRange.trim() : "");
            dictionary.setAliases(JSON.toJSONString(new ArrayList<>(aliases)));
            itemDictionaryMapper.insert(dictionary);
        } else {
            Set<String> existingAliases = new LinkedHashSet<>();
            if (dictionary.getAliases() != null) {
                JSONArray arr = JSON.parseArray(dictionary.getAliases());
                for (int i = 0; i < arr.size(); i++) {
                    existingAliases.add(arr.getString(i));
                }
            }
            existingAliases.addAll(aliases);
            dictionary.setAliases(JSON.toJSONString(new ArrayList<>(existingAliases)));
            if (unit != null && !unit.trim().isEmpty()) dictionary.setUnit(unit.trim());
            if (referenceRange != null && !referenceRange.trim().isEmpty()) dictionary.setReferenceRange(referenceRange.trim());
            if (category != null && !category.trim().isEmpty()) dictionary.setCategory(category.trim());
            itemDictionaryMapper.updateById(dictionary);
        }

        // 4. 批量更新所有指标
        for (HealthItem item : allItems) {
            item.setStandardName(newStandardName.trim());
            item.setDictionaryId(dictionary.getId());
            item.setCategory(dictionary.getCategory());
            item.setUnit(dictionary.getUnit());
            item.setReferenceRange(dictionary.getReferenceRange());
            if (item.getValue() != null) {
                item.setStatus(determineStatus(item.getValue().doubleValue(), dictionary.getReferenceRange()));
            }
            healthItemMapper.updateById(item);
        }
    }

    @Override
    public List<Map<String, Object>> scanDuplicateIndicators(Long userId) {
        // 1. 获取用户所有不重复的指标名称
        List<HealthItem> allItems = healthItemMapper.selectList(
                new LambdaQueryWrapper<HealthItem>()
                        .eq(HealthItem::getUserId, userId)
        );
        Set<String> names = new LinkedHashSet<>();
        for (HealthItem item : allItems) {
            String name = item.getStandardName();
            if (name == null || name.trim().isEmpty()) {
                name = item.getOriginalName();
            }
            if (name != null && !name.trim().isEmpty()) {
                names.add(name.trim());
            }
        }

        if (names.size() < 2) {
            return new ArrayList<>();
        }

        // 2. 调用AI扫描重复指标
        String aiResult = aiService.findDuplicateIndicators(new ArrayList<>(names));

        // 3. 解析AI返回的JSON
        try {
            JSONObject resultJson = JSON.parseObject(aiResult);
            JSONArray groups = resultJson.getJSONArray("groups");
            if (groups == null || groups.isEmpty()) {
                return new ArrayList<>();
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < groups.size(); i++) {
                JSONObject group = groups.getJSONObject(i);
                Map<String, Object> groupMap = new HashMap<>();
                groupMap.put("names", group.getJSONArray("names").toJavaList(String.class));
                groupMap.put("reason", group.getString("reason"));
                result.add(groupMap);
            }
            return result;
        } catch (Exception e) {
            // AI返回格式异常，返回空列表
            return new ArrayList<>();
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"trendData", "categoryTrendData", "reportDetail", "userReports"}, allEntries = true)
    public void updateReferenceRange(String standardName, String referenceRange) {
        if (standardName == null || standardName.trim().isEmpty()) return;
        if (referenceRange == null || referenceRange.trim().isEmpty()) return;

        // 1. 更新或创建 item_dictionary 记录
        ItemDictionary dictionary = itemDictionaryMapper.selectOne(
                new LambdaQueryWrapper<ItemDictionary>()
                        .eq(ItemDictionary::getStandardName, standardName.trim()));
        if (dictionary != null) {
            dictionary.setReferenceRange(referenceRange.trim());
            itemDictionaryMapper.updateById(dictionary);
        } else {
            dictionary = new ItemDictionary();
            dictionary.setStandardName(standardName.trim());
            dictionary.setCategory("其他");
            dictionary.setUnit("");
            dictionary.setReferenceRange(referenceRange.trim());
            dictionary.setAliases("[]");
            itemDictionaryMapper.insert(dictionary);
        }

        // 2. 更新所有关联的 health_item 记录
        List<HealthItem> items = healthItemMapper.selectList(
                new LambdaQueryWrapper<HealthItem>()
                        .eq(HealthItem::getStandardName, standardName.trim()));
        for (HealthItem item : items) {
            item.setReferenceRange(referenceRange.trim());
            if (item.getValue() != null) {
                String status = determineStatus(item.getValue().doubleValue(), referenceRange.trim());
                if (status != null) item.setStatus(status);
            }
            healthItemMapper.updateById(item);
        }
    }

    @Override
    public Map<String, Object> exportAllData(Long userId) {
        Map<String, Object> result = new HashMap<>();

        // 1. 用户信息
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(404, "用户不存在");
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("gender", user.getGender());
        userMap.put("birthDate", user.getBirthDate());
        result.put("user", userMap);

        // 2. 所有报告及指标（包括待审核和已确认）
        List<HealthReport> reports = healthReportMapper.selectList(
                new LambdaQueryWrapper<HealthReport>()
                        .eq(HealthReport::getUserId, userId)
                        .orderByAsc(HealthReport::getReportDate)
        );

        List<Map<String, Object>> reportList = new ArrayList<>();
        for (HealthReport report : reports) {
            Map<String, Object> reportMap = new HashMap<>();
            reportMap.put("id", report.getId());
            reportMap.put("date", report.getReportDate());
            reportMap.put("hospital", report.getHospital());

            List<HealthItem> items = healthItemMapper.selectList(
                    new LambdaQueryWrapper<HealthItem>()
                            .eq(HealthItem::getReportId, report.getId())
            );
            List<Map<String, Object>> itemList = new ArrayList<>();
            for (HealthItem item : items) {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("id", item.getId());
                itemMap.put("name", item.getStandardName() != null ? item.getStandardName() : item.getOriginalName());
                itemMap.put("originalName", item.getOriginalName());
                itemMap.put("category", item.getCategory());
                itemMap.put("value", item.getValue());
                itemMap.put("unit", item.getUnit());
                itemMap.put("referenceRange", item.getReferenceRange());
                itemMap.put("status", item.getStatus());
                itemList.add(itemMap);
            }
            reportMap.put("items", itemList);
            reportList.add(reportMap);
        }
        result.put("reports", reportList);

        // 3. 指标字典
        List<ItemDictionary> dictionaries = itemDictionaryMapper.selectList(null);
        List<Map<String, Object>> dictList = new ArrayList<>();
        for (ItemDictionary dict : dictionaries) {
            Map<String, Object> dictMap = new HashMap<>();
            dictMap.put("standardName", dict.getStandardName());
            dictMap.put("category", dict.getCategory());
            dictMap.put("unit", dict.getUnit());
            dictMap.put("referenceRange", dict.getReferenceRange());
            dictList.add(dictMap);
        }
        result.put("dictionary", dictList);

        // 4. AI诊断记录
        List<AiDiagnosis> diagnoses = aiDiagnosisMapper.selectList(
                new LambdaQueryWrapper<AiDiagnosis>()
                        .eq(AiDiagnosis::getUserId, userId)
                        .orderByDesc(AiDiagnosis::getDiagnosisDate)
        );
        List<Map<String, Object>> diagList = new ArrayList<>();
        for (AiDiagnosis diag : diagnoses) {
            Map<String, Object> diagMap = new HashMap<>();
            diagMap.put("date", diag.getDiagnosisDate());
            diagMap.put("content", diag.getDiagnosisContent());
            diagList.add(diagMap);
        }
        result.put("diagnoses", diagList);

        return result;
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