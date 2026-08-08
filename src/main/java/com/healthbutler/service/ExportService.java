package com.healthbutler.service;

/**
 * 导出服务 — 生成体检数据的独立 HTML 报告
 */
public interface ExportService {

    /**
     * 生成用户全部数据的 HTML 报告
     * @param userId 用户ID
     * @return 完整的 HTML 字符串，可直接下载
     */
    String generateHtmlReport(Long userId);
}
