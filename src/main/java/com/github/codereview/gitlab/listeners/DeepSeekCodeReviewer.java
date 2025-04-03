package com.github.codereview.gitlab.listeners;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DeepSeekCodeReviewer {

    private static final String API_KEY = "your_api_key_here";
    private static final String API_URL = "https://api.deepseek.com/v3/chat/completions";

    public static void main(String[] args) {
        // 待审查的代码示例
        String codeToReview = """
                def calculate_average(numbers):
                    sum = 0
                    for num in numbers:
                        sum += num
                    return sum / len(numbers)
                
                def validate_user(username, password):
                    if username == "admin" and password == "123456":
                        return True
                    return False
                """;

        // 构建审查提示词
        String reviewPrompt = buildReviewPrompt(codeToReview);

        // 调用API进行代码审查
        String reviewResult = getCodeReview(reviewPrompt);

        System.out.println("代码审查结果：");
        System.out.println(reviewResult);
    }

    private static String buildReviewPrompt(String code) {
        return String.format("""
            请作为资深代码评审专家，对我的代码进行专业评审。请按照以下要求输出：

            1. 评审维度
            - 代码结构
            - 安全性
            - 性能
            - 可维护性
            - 错误处理
            - 符合阿里巴巴代码规范

            3. 输出格式要求
            - 使用Markdown表格分类列出问题
            - 每个问题需包含：文件位置、问题描述、严重程度(高/中/低)、改进建议
            - 特别标注安全相关风险
            - 最后给出整体评价和改进优先级建议

            以下是待评审代码：
            ```%s
            %s
            ```

            请开始评审。
            """,  code);
    }

    private static String getCodeReview(String prompt) {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        // 构建请求体JSON
        String requestBody = String.format("""
            {
                "model": "deepseek-coder",
                "messages": [
                    {
                        "role": "user",
                        "content": "%s"
                    }
                ],
                "temperature": 0.2,
                "max_tokens": 2000
            }
            """, escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return extractContentFromResponse(response.body());
            } else {
                return "API请求失败，状态码: " + response.statusCode() + "\n响应: " + response.body();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "API请求异常: " + e.getMessage();
        }
    }

    private static String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String extractContentFromResponse(String jsonResponse) {
        // 简单提取content字段，实际应用中应该使用JSON解析库
        int start = jsonResponse.indexOf("\"content\":\"") + 11;
        int end = jsonResponse.indexOf("\"", start);
        if (start >= 0 && end > start) {
            return jsonResponse.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"");
        }
        return "无法解析API响应: " + jsonResponse;
    }
}