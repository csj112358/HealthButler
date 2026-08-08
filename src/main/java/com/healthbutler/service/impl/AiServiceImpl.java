package com.healthbutler.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.healthbutler.exception.BusinessException;
import com.healthbutler.service.AiService;
import com.healthbutler.vo.OcrResult;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.model.vision}")
    private String visionModel;

    @Value("${ai.model.text}")
    private String textModel;

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

    // ==================== 公共方法 ====================

    @Override
    public OcrResult extractReportData(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException(400, "图片数据为空");
        }

        // 压缩图片：限制最大宽度 2048px，JPEG 质量 0.7，避免 base64 体积过大导致超时
        byte[] compressed = compressImage(imageBytes, 2048, 0.7f);
        System.out.println("[Kimi API] 图片压缩: " + (imageBytes.length / 1024) + "KB -> " + (compressed.length / 1024) + "KB");

        String imageBase64 = Base64.getEncoder().encodeToString(compressed);
        String prompt = """
                你是一个高精度的医疗单据提取系统。请识别这张体检报告图片，提取所有的检测项目、结果、单位及参考范围，严格输出以下 JSON 格式，不含任何多余文字和 Markdown 标记：
                { "report_date": "YYYY-MM-DD", "hospital": "医院名称", "items": [ { "name": "指标名称", "category": "所属分类", "value": 浮点数, "unit": "单位", "reference_range": "参考范围", "status": "normal/high/low" } ] }

                注意：
                1. report_date 必须是 YYYY-MM-DD 格式的日期，如果图片中找不到日期，请返回空字符串 ""
                2. hospital 返回医院名称，如果找不到返回空字符串 ""
                3. value 必须是数字，不能是字符串，如果结果是文本型（如"阴性"、"未见异常"），请跳过该项
                4. status 根据检测值和参考范围判断：在范围内为 normal，高于范围为 high，低于范围为 low
                5. 【重要】每个 item 的 category 必须填写！根据指标名称和报告中的段落标题推断分类，必须从以下列表中选择：血常规、肝功能、肾功能、血脂、血糖、血压、甲状腺、尿常规、电解质、乙肝、肿瘤标志物、其他。例如：白细胞→血常规，肌酐→肾功能，谷丙转氨酶→肝功能。如果完全无法判断，填"其他"。""";

        // 构建多模态消息（图片在前，文本在后 — Kimi 视觉 API 要求此顺序）
        JSONObject imagePart = new JSONObject();
        imagePart.put("type", "image_url");
        JSONObject imageUrlObj = new JSONObject();
        imageUrlObj.put("url", "data:image/jpeg;base64," + imageBase64);
        imagePart.put("image_url", imageUrlObj);

        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        textPart.put("text", prompt);

        JSONArray contentArray = new JSONArray();
        contentArray.add(imagePart);
        contentArray.add(textPart);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", contentArray);

        JSONArray messages = new JSONArray();
        messages.add(userMsg);

        String content = callAiApi(visionModel, 4096, messages);
        return parseOcrResult(content);
    }

    @Override
    public OcrResult extractFromText(String text) {
        String prompt = """
                你是一个高精度的医疗单据提取系统。以下是从体检报告中提取的文字内容，请从中提取所有的检测项目、结果、单位及参考范围，严格输出以下 JSON 格式，不含任何多余文字和 Markdown 标记：
                { "report_date": "YYYY-MM-DD", "hospital": "医院名称", "items": [ { "name": "指标名称", "category": "所属分类", "value": 浮点数, "unit": "单位", "reference_range": "参考范围", "status": "normal/high/low" } ] }

                注意：
                1. report_date 必须是 YYYY-MM-DD 格式的日期，如果文字中找不到日期，请返回空字符串 ""
                2. hospital 返回医院名称，如果找不到返回空字符串 ""
                3. value 必须是数字，不能是字符串，如果结果是文本型（如"阴性"、"未见异常"），请跳过该项
                4. status 根据检测值和参考范围判断：在范围内为 normal，高于范围为 high，低于范围为 low
                5. 【重要】每个 item 的 category 必须填写！根据指标名称和报告中的段落标题推断分类，必须从以下列表中选择：血常规、肝功能、肾功能、血脂、血糖、血压、甲状腺、尿常规、电解质、乙肝、肿瘤标志物、其他。例如：白细胞→血常规，肌酐→肾功能，谷丙转氨酶→肝功能。如果完全无法判断，填"其他"。

                以下是体检报告文字内容：
                """ + text;

        JSONArray messages = new JSONArray();
        messages.add(userMsg(prompt));

        String content = callAiApi(textModel, 4096, messages);
        return parseOcrResult(content);
    }

    @Override
    public String generateDiagnosis(List<Map<String, Object>> historicalData, Map<String, Object> latestReport) {
        String systemPrompt = """
                # Role: 终身私人健康顾问
                ## Profile: 拥有 20 年经验的全科医生，擅长通过纵向对比历年体检数据发现潜在风险，给出专业的建议。
                ## Rules:
                1. 准确引用数据，指出好转、恶化或异常的指标。
                2. 进行多指标的交叉关联分析（如血压与血脂的综合风险）。
                3. 术语必须通俗易懂，并在回答末尾强制附加医疗免责声明。
                ## Output Format:
                请输出 Markdown 格式，严格包含以下结构：### 📊 核心指标变化概览、### 🩺 异常指标深度解读、### 💡 个性化健康建议、### ⚠️ 免责声明。""";

        JSONArray messages = new JSONArray();
        messages.add(systemMsg(systemPrompt));
        messages.add(userMsg(buildDiagnosisUserMessage(historicalData, latestReport)));

        return callAiApi(textModel, 4096, messages);
    }

    @Override
    public String findDuplicateIndicators(List<String> indicatorNames) {
        String prompt = "以下是用户的体检指标名称列表：\n\n" +
                String.join("\n", indicatorNames) +
                "\n\n请找出其中**语义相同但名称不同**的指标组（例如不同医院对同一指标的不同叫法），将它们分为一组。\n" +
                "判断标准：缩写和全称相同（如 ALT = 谷丙转氨酶）、中英文相同、同义词相同。\n" +
                "只返回有把握的重复组，不相关的指标不要强行分组。\n" +
                "严格输出以下 JSON 格式，不含任何多余文字和 Markdown 标记：\n" +
                "{\"groups\": [{\"names\": [\"名称1\", \"名称2\"], \"reason\": \"分组原因说明\"}]}\n" +
                "如果没有发现重复，返回 {\"groups\": []}";

        JSONArray messages = new JSONArray();
        messages.add(userMsg(prompt));

        return callAiApi(textModel, 2048, messages);
    }

    @Override
    public String suggestReferenceRanges(List<Map<String, String>> indicators) {
        StringBuilder items = new StringBuilder();
        for (Map<String, String> ind : indicators) {
            items.append("- ").append(ind.get("name"));
            if (ind.get("category") != null && !ind.get("category").isEmpty()) {
                items.append("（分类：").append(ind.get("category")).append("）");
            }
            if (ind.get("unit") != null && !ind.get("unit").isEmpty()) {
                items.append("，单位：").append(ind.get("unit"));
            }
            items.append("\n");
        }

        String prompt = "你是一位资深检验科医生。以下体检指标缺少参考范围，请根据医学知识为每个指标建议通用的成人参考范围。\n\n" +
                "指标列表：\n" + items + "\n" +
                "要求：\n" +
                "1. 严格输出 JSON 数组格式，不含 Markdown 标记和多余文字\n" +
                "2. 每项格式：{\"name\":\"指标名称\",\"suggestedRange\":\"建议范围(如3.5-9.5或<1.0等)\",\"reason\":\"简要医学依据(20字以内)\"}\n" +
                "3. 参考范围应基于中国成人通用标准\n" +
                "4. 只返回有把握的指标建议，不确定的不要编造\n" +
                "5. 如果所有指标都无法给出建议，返回空数组 []";

        JSONArray messages = new JSONArray();
        messages.add(userMsg(prompt));

        return callAiApi(textModel, 2048, messages);
    }

    // ==================== 私有方法 ====================

    /**
     * 统一的 AI API 调用，返回清理后的内容文本
     */
    private String callAiApi(String model, int maxTokens, JSONArray messages) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("messages", messages);

        String bodyJson = requestBody.toJSONString();
        log.debug("AI API 请求: model={}, bodyLen={}", model, bodyJson.length());

        Request request = new Request.Builder()
                .url(apiUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(JSON_MEDIA, bodyJson))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = "";
                try {
                    okhttp3.ResponseBody body = response.body();
                    if (body != null) errorBody = body.string();
                } catch (Exception ignored) {}
                System.out.println("[Kimi API] HTTP " + response.code() + " 错误: " + errorBody);
                throw new BusinessException(500, "AI API调用失败: HTTP " + response.code() + " - " + errorBody);
            }
            String responseBody = response.body().string();
            JSONObject jsonResponse = JSON.parseObject(responseBody);
            return extractContent(jsonResponse);
        } catch (IOException e) {
            throw new BusinessException(500, "调用AI API失败: " + e.getMessage());
        }
    }

    /** 从 API 响应中提取 choices[0].message.content 并清理 Markdown */
    private String extractContent(JSONObject jsonResponse) {
        String content = jsonResponse
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
        return cleanMarkdown(content);
    }

    /** 清理 AI 返回的 Markdown 代码块标记 */
    private String cleanMarkdown(String content) {
        if (content == null) return "";
        content = content.trim();
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        return content.trim();
    }

    /** 构建 user role 消息 */
    private JSONObject userMsg(String content) {
        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        msg.put("content", content);
        return msg;
    }

    /** 构建 system role 消息 */
    private JSONObject systemMsg(String content) {
        JSONObject msg = new JSONObject();
        msg.put("role", "system");
        msg.put("content", content);
        return msg;
    }

    /** 解析 OCR 结果 JSON */
    private OcrResult parseOcrResult(String jsonStr) {
        try {
            JSONObject json = JSON.parseObject(jsonStr);
            OcrResult result = new OcrResult();
            result.setReportDate(json.getString("report_date"));
            result.setHospital(json.getString("hospital"));
            JSONArray itemsArray = json.getJSONArray("items");
            List<OcrResult.Item> items = itemsArray.toJavaList(OcrResult.Item.class);
            result.setItems(items);
            return result;
        } catch (Exception e) {
            throw new BusinessException(500, "解析OCR结果失败: " + e.getMessage());
        }
    }

    /**
     * 压缩图片：缩放到最大宽度并降低 JPEG 质量，减少 API 传输体积
     */
    private byte[] compressImage(byte[] imageBytes, int maxWidth, float quality) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                // 无法解析的格式（如 PDF），原样返回
                return imageBytes;
            }

            int w = original.getWidth();
            int h = original.getHeight();
            if (w <= maxWidth) {
                // 尺寸已够小，只做 JPEG 压缩
                return encodeJpeg(original, quality);
            }

            // 等比缩放
            int newH = (int) ((double) h / w * maxWidth);
            BufferedImage scaled = new BufferedImage(maxWidth, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, maxWidth, newH, null);
            g.dispose();

            return encodeJpeg(scaled, quality);
        } catch (Exception e) {
            System.out.println("[Kimi API] 图片压缩失败，使用原图: " + e.getMessage());
            return imageBytes;
        }
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (writers.hasNext()) {
            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
        } else {
            ImageIO.write(image, "jpg", out);
        }
        return out.toByteArray();
    }

    /** 构建诊断用户消息 */
    private String buildDiagnosisUserMessage(List<Map<String, Object>> historicalData, Map<String, Object> latestReport) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下历年体检数据，生成健康诊断报告：\n\n");

        sb.append("## 历年体检数据\n");
        for (Map<String, Object> report : historicalData) {
            sb.append("### ").append(report.get("report_date")).append("\n");
            sb.append("医院：").append(report.get("hospital")).append("\n");
            sb.append("指标明细：\n");

            @SuppressWarnings("unchecked")
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

        sb.append("## 最新报告\n");
        sb.append("日期：").append(latestReport.get("report_date")).append("\n");
        sb.append("医院：").append(latestReport.get("hospital")).append("\n");
        sb.append("指标明细：\n");

        @SuppressWarnings("unchecked")
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
