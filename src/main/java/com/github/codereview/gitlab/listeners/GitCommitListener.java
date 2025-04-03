package com.github.codereview.gitlab.listeners;

import com.github.codereview.gitlab.settings.CodeReviewSettings;
import com.intellij.dvcs.push.PushInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Git提交监听器，用于在提交代码前进行代码审查
 */
public class GitCommitListener extends CheckinHandlerFactory {
    private static final Logger LOG = Logger.getInstance(GitCommitListener.class);

    @Override
    public @NotNull CheckinHandler createHandler(@NotNull CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
        LOG.info("Creating checkin handler for project: " + panel.getProject().getName());
        return new CheckinHandler() {
            @Override
            public ReturnResult beforeCheckin() {
                LOG.info("Before checkin triggered"); // 添加调试日志
                // 修复后代码：
                CodeReviewSettings settings = ApplicationManager.getApplication().getService(CodeReviewSettings.class);                        
                // 检查是否启用代码审查
                if (!settings.isEnableReview()) {
                    LOG.info("Code review is not enabled, skipping review."); // 添加调试日志
                    return ReturnResult.COMMIT;
                }
                
                // 获取提交的文件和变更内容
                Project project = panel.getProject();
                List<String> changes = panel.getSelectedChanges().stream()
                        .map(change -> {
                            if (change.getBeforeRevision() != null && change.getAfterRevision() != null) {
                                try {
                                    return "文件: " + change.getVirtualFile().getPath() + "\n" +
                                           "变更内容: " + change.getAfterRevision().getContent();
                                } catch (VcsException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            return "";
                        })
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                
                if (changes.isEmpty()) {
                    LOG.info("No changes detected, skipping review."); // 添加调试日志
                    return ReturnResult.COMMIT;
                }
                
                // 调用API进行代码审查
                try {
                    LOG.info("Performing code review for changes."); // 添加调试日志
                    String reviewResult = performCodeReview(changes, settings);
                    
                    // 显示审查结果
                    int result = Messages.showYesNoDialog(
                            project,
                            "代码审查结果:\n" + reviewResult + "\n\n是否继续提交?",
                            "GitLab代码审查",
                            "继续提交",
                            "取消提交",
                            Messages.getInformationIcon());
                    
                    return result == Messages.YES ? ReturnResult.COMMIT : ReturnResult.CANCEL;
                } catch (Exception e) {
                    LOG.error("代码审查失败", e);
                    int result = Messages.showYesNoDialog(
                            project,
                            "代码审查失败: " + e.getMessage() + "\n\n是否继续提交?",
                            "GitLab代码审查",
                            "继续提交",
                            "取消提交",
                            Messages.getErrorIcon());
                    
                    return result == Messages.YES ? ReturnResult.COMMIT : ReturnResult.CANCEL;
                }
            }
        };
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
            - 使用标题、列表和加粗文本格式输出问题
            - 每个问题按以下格式输出：
              > **问题类型**：[类型]
              > **文件位置**：[位置]
              > **问题描述**：[描述]
              > **严重程度**：[高/中/低]
              > **改进建议**：[建议]
            - 安全相关风险使用加粗标记：**[安全风险]**
            - 最后使用标题和列表给出整体评价和改进优先级建议

            以下是待评审代码：
            ```java
            %s
            ```

            请开始评审,然后按照输入格式要求输出评审结果。
            """,  code);
    }


    private static String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    /**
     * 调用智谱API进行代码审查
     *
     * @param changes 代码变更内容
     * @param settings 插件设置
     * @return 审查结果
     * @throws Exception 如果API调用失败
     */
    private String performCodeReview(List<String> changes, CodeReviewSettings settings) throws Exception {
        String reviewResult = null;
        String apiUrl = settings.getApiUrl();
        String apiKey = settings.getApiKey();
        String modelName = settings.getModelName();
        int maxTokens = settings.getMaxTokens();
        double temperature = settings.getTemperature();
        
        // 构建请求内容
        String changesContent = String.join("\n\n", changes);
        String prompt =buildReviewPrompt(changesContent);
        
        String requestBody = String.format(
                "{\"stream\":false,\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"max_tokens\":%d,\"temperature\":%f}",
                modelName,
                escapeJson(prompt),
                maxTokens,
                temperature
        );
        
        // 记录请求参数
        LOG.info("API请求参数 - URL: " + apiUrl);
        LOG.info("API请求参数 - 模型: " + modelName);
        LOG.info("API请求参数 - 最大Token数: " + maxTokens);
        LOG.info("API请求参数 - 温度: " + temperature);
        LOG.info("API请求参数 - 请求体: " + requestBody);
        LOG.info("API请求参数 - 请求体长度: " + requestBody.length() + " 字节");
        
        // 发送HTTP请求
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setDoOutput(true);
        
        LOG.info("开始发送API请求...");
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        // 读取响应
        int responseCode = connection.getResponseCode();
        LOG.info("API响应状态码: " + responseCode);
        
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                
                String responseStr = response.toString();
                LOG.info("API响应内容长度: " + responseStr.length() + " 字节");
                
                // 记录响应的前200个字符，避免日志过长
                String responsePreview = responseStr.length() > 200 ? 
                        responseStr.substring(0, 200) + "..." : responseStr;
                LOG.info("API响应内容预览: " + responsePreview);
                
                // 解析响应获取审查结果
                // 这里简化处理，实际应该解析JSON响应
                if (responseStr.contains("\"content\":")) {
                    int startIndex = responseStr.indexOf("\"content\":") + 11;
                    int endIndex = responseStr.indexOf("\"", startIndex);
                    if (endIndex > startIndex) {
                        String result = responseStr.substring(startIndex, endIndex)
                                .replace("\\n", "\n")
                                .replace("\\\"", "\"");
                        LOG.info("成功解析API响应，获取到审查结果");
                        reviewResult = result;
                        // 发送企业微信机器人通知
                        sendWeChatBotNotification(reviewResult, settings);
                        return reviewResult;
                    }
                }
                
                LOG.warn("无法解析API响应: " + responsePreview);
                return "无法解析API响应: " + responseStr;
            }
        } else {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                String errorResponse = response.toString();
                LOG.error("API请求失败，状态码: " + responseCode + ", 错误信息: " + errorResponse);
                throw new Exception("API请求失败，状态码: " + responseCode + ", 错误信息: " + errorResponse);
            }
        }
    }

    /**
     * 发送企业微信机器人通知
     *
     * @param reviewResult 代码审查结果
     * @param settings 插件设置
     * @throws Exception 如果发送通知失败
     */
    private void sendWeChatBotNotification(String reviewResult, CodeReviewSettings settings) throws Exception {
        String webhookUrl = settings.getWebhookUrl();
        
        // 构建请求体
        String requestBody = String.format(
                "{\"msgtype\":\"markdown\",\"markdown\":{\"content\":\"%s\"}}",
                escapeJson(reviewResult)
        );
        
        // 发送HTTP请求
        URL url = new URL(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        
        LOG.info("开始发送企业微信机器人通知...");
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        // 读取响应
        int responseCode = connection.getResponseCode();
        LOG.info("企业微信机器人响应状态码: " + responseCode);
        
        if (responseCode != HttpURLConnection.HTTP_OK) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                String errorResponse = response.toString();
                LOG.error("发送企业微信机器人通知失败: " + errorResponse);
                throw new Exception("发送企业微信机器人通知失败: " + errorResponse);
            }
        }
    }
}