package com.healthbutler.controller;

import com.healthbutler.entity.HealthReport;
import com.healthbutler.service.HealthReportService;
import com.healthbutler.vo.DiagnosisResult;
import com.healthbutler.vo.TrendData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONObject;

@RestController
@RequestMapping("/api/report")
public class HealthReportController {

    @Autowired
    private HealthReportService healthReportService;

    /**
     * 上传体检报告图片并进行OCR识别
     */
    @PostMapping("/upload")
    public Map<String, Object> uploadReport(
            @RequestParam("userId") Long userId,
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long reportId = healthReportService.uploadAndExtract(userId, file);
            result.put("code", 200);
            result.put("message", "上传成功，请审核识别结果");
            result.put("data", reportId);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "上传失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 通过文字内容创建体检报告（用户手动粘贴）
     */
    @PostMapping("/text")
    public Map<String, Object> createFromText(@RequestBody JSONObject body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String text = body.getString("text");
            if (text == null || text.trim().isEmpty()) {
                result.put("code", 400);
                result.put("message", "请输入体检报告文字");
                return result;
            }
            Map<String, Object> data = healthReportService.createFromText(userId, text);
            result.put("code", 200);
            result.put("message", "解析成功，请审核识别结果");
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "解析失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 确认报告数据（用户审核后，可附带编辑后的数据）
     */
    @PostMapping("/confirm/{reportId}")
    public Map<String, Object> confirmReport(
            @PathVariable Long reportId,
            @RequestBody(required = false) Map<String, Object> editedData) {
        Map<String, Object> result = new HashMap<>();
        try {
            healthReportService.confirmReport(reportId, editedData);
            result.put("code", 200);
            result.put("message", "确认成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "确认失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 更新报告数据（编辑指标明细、日期、医院等）
     */
    @PutMapping("/update/{reportId}")
    public Map<String, Object> updateReport(
            @PathVariable Long reportId,
            @RequestBody Map<String, Object> editedData) {
        Map<String, Object> result = new HashMap<>();
        try {
            healthReportService.updateReport(reportId, editedData);
            result.put("code", 200);
            result.put("message", "更新成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "更新失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 删除报告
     */
    @DeleteMapping("/delete/{reportId}")
    public Map<String, Object> deleteReport(@PathVariable Long reportId) {
        Map<String, Object> result = new HashMap<>();
        try {
            healthReportService.deleteReport(reportId);
            result.put("code", 200);
            result.put("message", "删除成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "删除失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 获取用户所有报告列表
     */
    @GetMapping("/list/{userId}")
    public Map<String, Object> getUserReports(@PathVariable Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<HealthReport> reports = healthReportService.getUserReports(userId);
            result.put("code", 200);
            result.put("data", reports);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "获取失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 获取单个报告详情（包含指标明细）
     */
    @GetMapping("/detail/{reportId}")
    public Map<String, Object> getReportDetail(@PathVariable Long reportId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> detail = healthReportService.getReportDetail(reportId);
            result.put("code", 200);
            result.put("data", detail);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "获取失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 获取指标趋势数据（用于ECharts展示）
     */
    @GetMapping("/trend/{userId}/{standardName}")
    public Map<String, Object> getTrendData(
            @PathVariable Long userId,
            @PathVariable String standardName) {
        Map<String, Object> result = new HashMap<>();
        try {
            TrendData trendData = healthReportService.getTrendData(userId, standardName);
            result.put("code", 200);
            result.put("data", trendData);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "获取失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 获取同类指标趋势数据
     */
    @GetMapping("/trend/category/{userId}/{category}")
    public Map<String, Object> getCategoryTrendData(
            @PathVariable Long userId,
            @PathVariable String category) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<TrendData> trendDataList = healthReportService.getCategoryTrendData(userId, category);
            result.put("code", 200);
            result.put("data", trendDataList);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "获取失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 生成AI健康诊断
     */
    @PostMapping("/diagnosis")
    public Map<String, Object> generateDiagnosis(
            @RequestParam("userId") Long userId,
            @RequestParam("reportIds") List<Long> reportIds) {
        Map<String, Object> result = new HashMap<>();
        try {
            DiagnosisResult diagnosisResult = healthReportService.generateDiagnosis(userId, reportIds);
            result.put("code", 200);
            result.put("data", diagnosisResult);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "生成诊断失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 获取用户所有有数据的指标列表
     */
    @GetMapping("/indicators/{userId}")
    public Map<String, Object> getUserIndicators(@PathVariable Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> indicators = healthReportService.getUserIndicators(userId);
            result.put("code", 200);
            result.put("data", indicators);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "获取失败：" + e.getMessage());
        }
        return result;
    }
}