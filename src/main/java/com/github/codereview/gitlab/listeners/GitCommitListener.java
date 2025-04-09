package com.github.codereview.gitlab.listeners;

import com.github.codereview.gitlab.settings.CodeReviewSettings;
import com.github.codereview.gitlab.ui.StreamingReviewDialog;
import com.github.codereview.gitlab.util.StreamingResponseHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import okhttp3.OkHttpClient;

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

                    // 如果reviewResult为null，表示用户在流式对话框中取消了提交
                    if (reviewResult == null) {
                        return ReturnResult.CANCEL;
                    }

                    return ReturnResult.COMMIT;
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
                """, code);
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
     * @return 审查结果，如果用户取消则返回null
     * @throws Exception 如果API调用失败
     */
    private String performCodeReview(List<String> changes, CodeReviewSettings settings) throws Exception {
        String apiUrl = settings.getApiUrl();
        String apiKey = settings.getApiKey();
        String modelName = settings.getModelName();
        int maxTokens = settings.getMaxTokens();
        double temperature = settings.getTemperature();

        // 构建请求内容
        String changesContent = String.join("\n\n", changes);
        String prompt = buildReviewPrompt(changesContent);

        // 根据设置决定是否使用流式输出
        boolean useStreaming = settings.isEnableStreamingOutput();

        String requestBody = String.format(
                "{\"stream\":%b,\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"max_tokens\":%d,\"temperature\":%f}",
                useStreaming,
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
        LOG.info("API请求参数 - 请求体长度: " + requestBody.length() + " 字节");

        // 创建流式对话框
        Project project = ProjectManager.getInstance().getOpenProjects()[0];
        StreamingReviewDialog dialog = new StreamingReviewDialog(project, "GitLab代码审查");

        // 创建流式响应处理器
        StreamingResponseHandler responseHandler = new StreamingResponseHandler(dialog);

        // 设置完成回调
        responseHandler.setOnCompleteCallback(content -> {
            try {
                // 发送企业微信机器人通知
                sendWeChatBotNotification(content, settings);
            } catch (Exception e) {
                LOG.error("发送企业微信通知失败", e);
            }
        });

        // 创建OkHttp3请求
        okhttp3.RequestBody okHttpRequestBody = okhttp3.RequestBody.create(
                requestBody, okhttp3.MediaType.parse("application/json; charset=utf-8"));

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(apiUrl)
                .post(okHttpRequestBody)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .build();

        // 先初始化对话框，但不显示
        dialog.updateContent("正在准备代码审查，请稍候...");

        // 先记录日志
        LOG.info("开始发送流式API请求...");

        // 先启动流式响应处理
        CompletableFuture<String> future = responseHandler.handleStreamingResponse(request);

        // 然后显示对话框
        dialog.show();

        // 等待响应处理完成
        String finalReviewResult = future.get();

        // 根据对话框结果决定是否继续提交
        return dialog.isOK() ? finalReviewResult : null;
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

        LOG.info("开始发送企业微信机器人通知...");

        // 创建OkHttpClient
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        // 创建请求
        okhttp3.RequestBody okHttpRequestBody = okhttp3.RequestBody.create(
                requestBody, okhttp3.MediaType.parse("application/json; charset=utf-8"));

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(webhookUrl)
                .post(okHttpRequestBody)
                .header("Content-Type", "application/json")
                .build();

        // 执行请求
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorResponse = response.body() != null ? response.body().string() : "无响应体";
                LOG.error("发送企业微信机器人通知失败，状态码: " + response.code() + ", 错误信息: " + errorResponse);
                throw new Exception("发送企业微信机器人通知失败，状态码: " + response.code() + ", 错误信息: " + errorResponse);
            }
            LOG.info("企业微信机器人通知发送成功，状态码: " + response.code());
        } catch (IOException e) {
            LOG.error("发送企业微信机器人通知失败", e);
            throw new Exception("发送企业微信机器人通知失败: " + e.getMessage());
        }
    }
}