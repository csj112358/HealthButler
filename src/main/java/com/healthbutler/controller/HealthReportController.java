package com.healthbutler.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.healthbutler.entity.HealthReport;
import com.healthbutler.exception.BusinessException;
import com.healthbutler.service.AiService;
import com.healthbutler.service.ExportService;
import com.healthbutler.service.HealthReportService;
import com.healthbutler.vo.DiagnosisResult;
import com.healthbutler.vo.Result;
import com.healthbutler.vo.TrendData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
public class HealthReportController {

    @Autowired
    private HealthReportService healthReportService;

    @Autowired
    private ExportService exportService;

    @Autowired
    private AiService aiService;

    @PostMapping("/upload")
    public Result<Long> uploadReport(
            @RequestParam("userId") Long userId,
            @RequestParam("file") MultipartFile file) {
        Long reportId = healthReportService.uploadAndExtract(userId, file);
        return Result.ok("上传成功，请审核识别结果", reportId);
    }

    @PostMapping("/text")
    public Result<Map<String, Object>> createFromText(@RequestBody JSONObject body) {
        Long userId = getCurrentUserId();
        String text = body.getString("text");
        if (text == null || text.trim().isEmpty()) {
            throw new BusinessException(400, "请输入体检报告文字");
        }
        Map<String, Object> data = healthReportService.createFromText(userId, text);
        return Result.ok("解析成功，请审核识别结果", data);
    }

    @PostMapping("/confirm/{reportId}")
    public Result<Void> confirmReport(
            @PathVariable Long reportId,
            @RequestBody(required = false) Map<String, Object> editedData) {
        healthReportService.confirmReport(reportId, editedData);
        return Result.ok("确认成功", null);
    }

    @PutMapping("/update/{reportId}")
    public Result<Void> updateReport(
            @PathVariable Long reportId,
            @RequestBody Map<String, Object> editedData) {
        healthReportService.updateReport(reportId, editedData);
        return Result.ok("更新成功", null);
    }

    @DeleteMapping("/delete/{reportId}")
    public Result<Void> deleteReport(@PathVariable Long reportId) {
        healthReportService.deleteReport(reportId);
        return Result.ok("删除成功", null);
    }

    @GetMapping("/list/{userId}")
    public Result<List<HealthReport>> getUserReports(@PathVariable Long userId) {
        List<HealthReport> reports = healthReportService.getUserReports(userId);
        return Result.ok(reports);
    }

    @GetMapping("/detail/{reportId}")
    public Result<Map<String, Object>> getReportDetail(@PathVariable Long reportId) {
        Map<String, Object> detail = healthReportService.getReportDetail(reportId);
        return Result.ok(detail);
    }

    @GetMapping("/trend/{userId}/{standardName}")
    public Result<TrendData> getTrendData(
            @PathVariable Long userId,
            @PathVariable String standardName) {
        TrendData trendData = healthReportService.getTrendData(userId, standardName);
        return Result.ok(trendData);
    }

    @GetMapping("/trend/category/{userId}/{category}")
    public Result<List<TrendData>> getCategoryTrendData(
            @PathVariable Long userId,
            @PathVariable String category) {
        List<TrendData> trendDataList = healthReportService.getCategoryTrendData(userId, category);
        return Result.ok(trendDataList);
    }

    @PostMapping("/diagnosis")
    public Result<DiagnosisResult> generateDiagnosis(
            @RequestParam("userId") Long userId,
            @RequestParam("reportIds") List<Long> reportIds) {
        DiagnosisResult diagnosisResult = healthReportService.generateDiagnosis(userId, reportIds);
        return Result.ok(diagnosisResult);
    }

    @PostMapping("/merge-items")
    public Result<Void> mergeItems(@RequestBody JSONObject body) {
        Long userId = getCurrentUserId();
        List<String> standardNames = body.getJSONArray("standardNames").toJavaList(String.class);
        String newStandardName = body.getString("newStandardName");
        String category = body.getString("category");
        String unit = body.getString("unit");
        String referenceRange = body.getString("referenceRange");
        healthReportService.mergeItems(userId, standardNames, newStandardName, category, unit, referenceRange);
        return Result.ok("合并成功", null);
    }

    @PostMapping("/scan-duplicates")
    public Result<List<Map<String, Object>>> scanDuplicates() {
        Long userId = getCurrentUserId();
        List<Map<String, Object>> groups = healthReportService.scanDuplicateIndicators(userId);
        return Result.ok(groups);
    }

    @GetMapping("/export/{userId}")
    public ResponseEntity<byte[]> exportAllData(@PathVariable Long userId) {
        String html = exportService.generateHtmlReport(userId);
        byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header("Content-Disposition",
                        "attachment;filename=\"体检数据_" + LocalDate.now() + ".html\"")
                .body(htmlBytes);
    }

    @PostMapping("/suggest-ranges")
    public Result<Object> suggestRanges(@RequestBody JSONObject body) {
        List<Map<String, String>> indicators = new ArrayList<>();
        for (Object item : body.getJSONArray("indicators")) {
            Map<String, Object> obj;
            if (item instanceof JSONObject) {
                obj = ((JSONObject) item).getInnerMap();
            } else if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                obj = map;
            } else {
                continue;
            }
            Map<String, String> m = new HashMap<>();
            m.put("name", obj.get("name") != null ? obj.get("name").toString() : "");
            m.put("category", obj.get("category") != null ? obj.get("category").toString() : "");
            m.put("unit", obj.get("unit") != null ? obj.get("unit").toString() : "");
            indicators.add(m);
        }
        String aiResult = aiService.suggestReferenceRanges(indicators);
        return Result.ok(JSON.parse(aiResult));
    }

    @PutMapping("/update-range")
    public Result<Void> updateReferenceRange(@RequestBody JSONObject body) {
        String name = body.getString("name");
        String referenceRange = body.getString("referenceRange");
        healthReportService.updateReferenceRange(name, referenceRange);
        return Result.ok("更新成功", null);
    }

    @GetMapping("/indicators/{userId}")
    public Result<List<Map<String, Object>>> getUserIndicators(@PathVariable Long userId) {
        List<Map<String, Object>> indicators = healthReportService.getUserIndicators(userId);
        return Result.ok(indicators);
    }

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
