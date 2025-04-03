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
        // �����Ĵ���ʾ��
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

        // ���������ʾ��
        String reviewPrompt = buildReviewPrompt(codeToReview);

        // ����API���д������
        String reviewResult = getCodeReview(reviewPrompt);

        System.out.println("�����������");
        System.out.println(reviewResult);
    }

    private static String buildReviewPrompt(String code) {
        return String.format("""
            ����Ϊ�����������ר�ң����ҵĴ������רҵ�����밴������Ҫ�������

            1. ����ά��
            - ����ṹ
            - ��ȫ��
            - ����
            - ��ά����
            - ������
            - ���ϰ���Ͱʹ���淶

            3. �����ʽҪ��
            - ʹ��Markdown�������г�����
            - ÿ��������������ļ�λ�á��������������س̶�(��/��/��)���Ľ�����
            - �ر��ע��ȫ��ط���
            - �������������ۺ͸Ľ����ȼ�����

            �����Ǵ�������룺
            ```%s
            %s
            ```

            �뿪ʼ����
            """,  code);
    }

    private static String getCodeReview(String prompt) {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        // ����������JSON
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
                return "API����ʧ�ܣ�״̬��: " + response.statusCode() + "\n��Ӧ: " + response.body();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "API�����쳣: " + e.getMessage();
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
        // ����ȡcontent�ֶΣ�ʵ��Ӧ����Ӧ��ʹ��JSON������
        int start = jsonResponse.indexOf("\"content\":\"") + 11;
        int end = jsonResponse.indexOf("\"", start);
        if (start >= 0 && end > start) {
            return jsonResponse.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"");
        }
        return "�޷�����API��Ӧ: " + jsonResponse;
    }
}