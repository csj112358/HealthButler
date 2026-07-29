package com.healthbutler.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.healthbutler.service.AiService;
import com.healthbutler.vo.OcrResult;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AiServiceImpl implements AiService {

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.model.vision}")
    private String visionModel;

    @Value("${ai.model.text}")
    private String textModel;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public OcrResult extractReportData(String imageUrl) {
        // OCR与数据结构化 Prompt
        String prompt = "你是一个高精度的医疗单据提取系统。请提取上传图片中所有的检测项目、结果、单位及参考范围，严格输出以下 JSON 格式，不含任何多余文字和 Markdown 标记：\n" +
                "{ \"report_date\": \"YYYY-MM-DD\", \"hospital\": \"医院名称\", \"items\": [ { \"name\": \"指标名称\", \"category\": \"所属分类(如肝功能)\", \"value\": 浮点数, \"unit\": \"单位\", \"reference_range\": \"参考范围\", \"status\": \"normal/high/low\" } ] }";

        try {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", visionModel);
            requestBody.put("max_tokens", 4096);

            JSONArray messages = new JSONArray();

            // 用户消息（包含图片和提示词）
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");

            JSONArray content = new JSONArray();

            // 图片内容
            JSONObject imageContent = new JSONObject();
            imageContent.put("type", "image_url");
            JSONObject imageUrlObj = new JSONObject();
            imageUrlObj.put("url", imageUrl);
            imageContent.put("image_url", imageUrlObj);
            content.add(imageContent);

            // 文本内容
            JSONObject textContent = new JSONObject();
            textContent.put("type", "text");
            textContent.put("text", prompt);
            content.add(textContent);

            userMessage.put("content", content);
            messages.add(userMessage);

            requestBody.put("messages", messages);

            // 发送请求
            Request request = new Request.Builder()
                    .url(apiUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(
                            MediaType.parse("application/json"),
                            requestBody.toJSONString()
                    ))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("AI API调用失败: " + response.code());
                }

                String responseBody = response.body().string();
                JSONObject jsonResponse = JSON.parseObject(responseBody);

                // 提取AI返回的内容
                String contentStr = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");

                // 解析JSON格式的OCR结果
                return parseOcrResult(contentStr);
            }
        } catch (IOException e) {
            throw new RuntimeException("调用AI API失败", e);
        }
    }

    @Override
    public OcrResult extractFromText(String text) {
        // 文字解析 Prompt
        String prompt = "你是一个高精度的医疗单据提取系统。以下是从体检报告中提取的文字内容，请从中提取所有的检测项目、结果、单位及参考范围，严格输出以下 JSON 格式，不含任何多余文字和 Markdown 标记：\n" +
                "{ \"report_date\": \"YYYY-MM-DD\", \"hospital\": \"医院名称\", \"items\": [ { \"name\": \"指标名称\", \"category\": \"所属分类(如肝功能)\", \"value\": 浮点数, \"unit\": \"单位\", \"reference_range\": \"参考范围\", \"status\": \"normal/high/low\" } ] }\n\n" +
                "注意：\n" +
                "1. report_date 必须是 YYYY-MM-DD 格式的日期，如果文字中找不到日期，请返回空字符串 \"\"\n" +
                "2. hospital 返回医院名称，如果找不到返回空字符串 \"\"\n" +
                "3. value 必须是数字，不能是字符串\n" +
                "4. status 根据检测值和参考范围判断：在范围内为 normal，高于范围为 high，低于范围为 low\n\n" +
                "以下是体检报告文字内容：\n" + text;

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", textModel);
            requestBody.put("max_tokens", 4096);

            JSONArray messages = new JSONArray();

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);

            requestBody.put("messages", messages);

            Request request = new Request.Builder()
                    .url(apiUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(
                            MediaType.parse("application/json"),
                            requestBody.toJSONString()
                    ))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("AI API调用失败: " + response.code());
                }

                String responseBody = response.body().string();
                JSONObject jsonResponse = JSON.parseObject(responseBody);

                String contentStr = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");

                return parseOcrResult(contentStr);
            }
        } catch (IOException e) {
            throw new RuntimeException("调用AI API失败", e);
        }
    }

    @Override
    public String generateDiagnosis(List<Map<String, Object>> historicalData, Map<String, Object> latestReport) {
        // 纵向健康诊断 Prompt
        String systemPrompt = "# Role: 终身私人健康顾问\n" +
                "## Profile: 拥有 20 年经验的全科医生，擅长通过纵向对比历年体检数据发现潜在风险，给出专业的建议。\n" +
                "## Rules:\n" +
                "1. 准确引用数据，指出好转、恶化或异常的指标。\n" +
                "2. 进行多指标的交叉关联分析（如血压与血脂的综合风险）。\n" +
                "3. 术语必须通俗易懂，并在回答末尾强制附加医疗免责声明。\n" +
                "## Output Format:\n" +
                "请输出 Markdown 格式，严格包含以下结构：### 📊 核心指标变化概览、### 🩺 异常指标深度解读、### 💡 个性化健康建议、### ⚠️ 免责声明。";

        // 构建用户消息（包含历年数据）
        String userMessage = buildDiagnosisUserMessage(historicalData, latestReport);

        try {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", textModel);
            requestBody.put("max_tokens", 4096);

            JSONArray messages = new JSONArray();

            // 系统消息
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);

            // 用户消息
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            requestBody.put("messages", messages);

            // 发送请求
            Request request = new Request.Builder()
                    .url(apiUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(
                            MediaType.parse("application/json"),
                            requestBody.toJSONString()
                    ))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("AI API调用失败: " + response.code());
                }

                String responseBody = response.body().string();
                JSONObject jsonResponse = JSON.parseObject(responseBody);

                // 提取AI返回的内容
                return jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
            }
        } catch (IOException e) {
            throw new RuntimeException("调用AI API失败", e);
        }
    }

    /**
     * 解析OCR结果JSON
     */
    private OcrResult parseOcrResult(String jsonStr) {
        try {
            // 清理可能的Markdown标记
            jsonStr = jsonStr.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();

            JSONObject json = JSON.parseObject(jsonStr);

            OcrResult result = new OcrResult();
            result.setReportDate(json.getString("report_date"));
            result.setHospital(json.getString("hospital"));

            JSONArray itemsArray = json.getJSONArray("items");
            List<OcrResult.Item> items = itemsArray.toJavaList(OcrResult.Item.class);
            result.setItems(items);

            return result;
        } catch (Exception e) {
            throw new RuntimeException("解析OCR结果失败", e);
        }
    }

    /**
     * 构建诊断用户消息
     */
    private String buildDiagnosisUserMessage(List<Map<String, Object>> historicalData, Map<String, Object> latestReport) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下历年体检数据，生成健康诊断报告：\n\n");

        // 历年数据
        sb.append("## 历年体检数据\n");
        for (Map<String, Object> report : historicalData) {
            sb.append("### ").append(report.get("report_date")).append("\n");
            sb.append("医院：").append(report.get("hospital")).append("\n");
            sb.append("指标明细：\n");

            List<Map<String, Object>> items = (List<Map<String, Object>>) report.get("items");
            for (Map<String, Object> item : items) {
                sb.append("- ").append(item.get("standard_name"))
                  .append(": ").append(item.get("value"))
                  .append(" ").append(item.get("unit"))
                  .append(" (参考范围: ").append(item.get("reference_range")).append(")")
                  .append(" [").append(item.get("status")).append("]\n");
            }
            sb.append("\n");
        }

        // 最新报告
        sb.append("## 最新报告\n");
        sb.append("日期：").append(latestReport.get("report_date")).append("\n");
        sb.append("医院：").append(latestReport.get("hospital")).append("\n");
        sb.append("指标明细：\n");

        List<Map<String, Object>> latestItems = (List<Map<String, Object>>) latestReport.get("items");
        for (Map<String, Object> item : latestItems) {
            sb.append("- ").append(item.get("standard_name"))
              .append(": ").append(item.get("value"))
              .append(" ").append(item.get("unit"))
              .append(" (参考范围: ").append(item.get("reference_range")).append(")")
              .append(" [").append(item.get("status")).append("]\n");
        }

        return sb.toString();
    }
}