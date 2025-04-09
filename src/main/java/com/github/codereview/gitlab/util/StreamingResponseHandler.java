package com.github.codereview.gitlab.util;

import com.github.codereview.gitlab.ui.StreamingReviewDialog;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 流式响应处理器
 * 用于处理API的流式响应并更新UI
 */
public class StreamingResponseHandler {
    private static final Logger LOG = Logger.getInstance(StreamingResponseHandler.class);
    private final StreamingReviewDialog dialog;
    private final AtomicBoolean isComplete = new AtomicBoolean(false);
    private final StringBuilder fullContent = new StringBuilder();
    private Consumer<String> onCompleteCallback;

    /**
     * 创建流式响应处理器
     *
     * @param dialog 用于显示流式内容的对话框
     */
    public StreamingResponseHandler(StreamingReviewDialog dialog) {
        this.dialog = dialog;
    }

    /**
     * 设置完成回调
     *
     * @param callback 完成时的回调函数，参数为完整内容
     */
    public void setOnCompleteCallback(Consumer<String> callback) {
        this.onCompleteCallback = callback;
    }

    /**
     * 使用OkHttp3处理流式响应
     *
     * @param request OkHttp3请求对象
     * @return 包含完整响应内容的Future
     */
    public CompletableFuture<String> handleStreamingResponse(Request request) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        // 重置状态
        fullContent.setLength(0);
        isComplete.set(false);
        
        // 先更新对话框，显示正在处理
        dialog.updateContent("正在进行代码审查，请稍候...");
        
        // 创建OkHttpClient，设置超时
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        // 异步执行请求
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                LOG.error("API请求失败", e);
                dialog.appendContent("\n\n**API请求失败: " + e.getMessage() + "**");
                completeProcessing();
                future.completeExceptionally(e);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorMsg = "API请求失败，状态码: " + response.code();
                    LOG.error(errorMsg);
                    dialog.appendContent("\n\n**" + errorMsg + "**");
                    completeProcessing();
                    future.completeExceptionally(new IOException(errorMsg));
                    response.close();
                    return;
                }
                
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    String errorMsg = "API响应体为空";
                    LOG.error(errorMsg);
                    dialog.appendContent("\n\n**" + errorMsg + "**");
                    completeProcessing();
                    future.completeExceptionally(new IOException(errorMsg));
                    response.close();
                    return;
                }
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    boolean hasReceivedContent = false;
                    int emptyLineCount = 0;
                    int contentCount = 0;
                    
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            emptyLineCount++;
                            // 如果连续收到多个空行，可能是连接问题
                            if (emptyLineCount > 10 && !hasReceivedContent) {
                                LOG.warn("连续收到多个空行，可能存在连接问题");
                                dialog.appendContent("\n\n可能存在网络连接问题，请检查网络或API配置...");
                            }
                            continue;
                        }
                        
                        emptyLineCount = 0; // 重置空行计数
                        LOG.debug("收到响应行: " + line);
                        
                        // 处理SSE格式的数据行 (data: {...})
                        if (line.startsWith("data: ")) {
                            String jsonData = line.substring(6); // 去掉 "data: " 前缀
                            
                            // 检查是否是流的结束
                            if (jsonData.equals("[DONE]")) {
                                LOG.info("流式响应接收完成");
                                completeProcessing();
                                break;
                            }
                            
                            // 解析JSON并提取内容
                            String content = extractContentFromJson(jsonData);
                            if (content != null && !content.isEmpty()) {
                                // 如果是第一个内容片段，更新UI并替换初始提示
                                if (contentCount == 0) {
                                    dialog.updateContent(content);
                                    fullContent.setLength(0); // 清空之前的内容
                                    fullContent.append(content);
                                } else {
                                    processContent(content);
                                }
                                contentCount++;
                                hasReceivedContent = true;
                            }
                        } else {
                            // 非SSE格式，可能是普通JSON响应
                            String content = extractContentFromJson(line);
                            if (content != null && !content.isEmpty()) {
                                // 如果是第一个内容片段，更新UI并替换初始提示
                                if (contentCount == 0) {
                                    dialog.updateContent(content);
                                    fullContent.setLength(0); // 清空之前的内容
                                    fullContent.append(content);
                                } else {
                                    processContent(content);
                                }
                                contentCount++;
                                hasReceivedContent = true;
                            }
                        }
                    }
                    
                    // 如果没有收到任何内容，显示提示信息
                    if (!hasReceivedContent) {
                        LOG.warn("未从响应中提取到任何内容");
                        dialog.updateContent("正在进行代码审查，请稍候...\n\n未能从API响应中提取到内容，请检查API配置和网络连接。");
                    }
                    
                    // 确保处理完成
                    completeProcessing();
                    future.complete(fullContent.toString());
                    
                } catch (Exception e) {
                    LOG.error("处理流式响应时发生错误", e);
                    dialog.appendContent("\n\n**处理响应时发生错误: " + e.getMessage() + "**");
                    completeProcessing();
                    future.completeExceptionally(e);
                } finally {
                    response.close();
                }
            }
        });
        
        return future;
    }
    
    /**
     * 处理流式响应（兼容旧版API，使用输入流）
     *
     * @param inputStream 响应输入流
     * @return 包含完整响应内容的Future
     */
    public CompletableFuture<String> handleStreamingResponse(InputStream inputStream) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        // 重置状态
        fullContent.setLength(0);
        isComplete.set(false);
        
        // 先更新对话框，显示正在处理
        dialog.updateContent("正在进行代码审查，请稍候...");
        
        Thread processingThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                boolean hasReceivedContent = false;
                int emptyLineCount = 0;
                int contentCount = 0;
                
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        emptyLineCount++;
                        // 如果连续收到多个空行，可能是连接问题
                        if (emptyLineCount > 10 && !hasReceivedContent) {
                            LOG.warn("连续收到多个空行，可能存在连接问题");
                            dialog.appendContent("\n\n可能存在网络连接问题，请检查网络或API配置...");
                        }
                        continue;
                    }
                    
                    emptyLineCount = 0; // 重置空行计数
                    LOG.debug("收到响应行: " + line);
                    
                    // 处理SSE格式的数据行 (data: {...})
                    if (line.startsWith("data: ")) {
                        String jsonData = line.substring(6); // 去掉 "data: " 前缀
                        
                        // 检查是否是流的结束
                        if (jsonData.equals("[DONE]")) {
                            LOG.info("流式响应接收完成");
                            completeProcessing();
                            break;
                        }
                        
                        // 解析JSON并提取内容
                        String content = extractContentFromJson(jsonData);
                        if (content != null && !content.isEmpty()) {
                            // 如果是第一个内容片段，更新UI并替换初始提示
                            if (contentCount == 0) {
                                dialog.updateContent(content);
                                fullContent.setLength(0); // 清空之前的内容
                                fullContent.append(content);
                            } else {
                                processContent(content);
                            }
                            contentCount++;
                            hasReceivedContent = true;
                        }
                    } else {
                        // 非SSE格式，可能是普通JSON响应
                        String content = extractContentFromJson(line);
                        if (content != null && !content.isEmpty()) {
                            // 如果是第一个内容片段，更新UI并替换初始提示
                            if (contentCount == 0) {
                                dialog.updateContent(content);
                                fullContent.setLength(0); // 清空之前的内容
                                fullContent.append(content);
                            } else {
                                processContent(content);
                            }
                            contentCount++;
                            hasReceivedContent = true;
                        }
                    }
                }
                
                // 如果没有收到任何内容，显示提示信息
                if (!hasReceivedContent) {
                    LOG.warn("未从响应中提取到任何内容");
                    dialog.updateContent("正在进行代码审查，请稍候...\n\n未能从API响应中提取到内容，请检查API配置和网络连接。");
                }
                
                // 确保处理完成
                completeProcessing();
                future.complete(fullContent.toString());
                
            } catch (Exception e) {
                LOG.error("处理流式响应时发生错误", e);
                dialog.appendContent("\n\n**处理响应时发生错误: " + e.getMessage() + "**");
                completeProcessing();
                future.completeExceptionally(e);
            }
        });
        
        processingThread.setDaemon(true);
        processingThread.start();
        
        return future;
    }

    /**
     * 从JSON响应中提取内容
     *
     * @param jsonData JSON数据
     * @return 提取的内容
     */
    private String extractContentFromJson(String jsonData) {
        try {
            if (jsonData.equals("[DONE]")) {
                return "";
            }
            
            JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();
            if (jsonObject.has("choices")) {
                JsonArray choices = jsonObject.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    
                    // 处理流式响应格式
                    if (choice.has("delta")) {
                        JsonObject delta = choice.getAsJsonObject("delta");
                        if (delta.has("content")) {
                            return delta.get("content").getAsString();
                        }
                    }
                    // 处理非流式响应格式
                    else if (choice.has("message")) {
                        JsonObject message = choice.getAsJsonObject("message");
                        if (message.has("content")) {
                            return message.get("content").getAsString();
                        }
                    }
                    // 直接获取content字段
                    else if (choice.has("content")) {
                        return choice.get("content").getAsString();
                    }
                }
            }
            
            // 记录无法解析的JSON数据
            LOG.debug("未能从JSON中提取内容: " + jsonData);
            
        } catch (Exception e) {
            LOG.warn("解析JSON响应失败: " + jsonData, e);
        }
        return "";
    }

    /**
     * 处理内容片段
     *
     * @param content 内容片段
     */
    private void processContent(String content) {
        if (content != null && !content.isEmpty()) {
            // 移除可能的转义字符
            content = content.replace("\\n", "\n")
                           .replace("\\r", "\r")
                           .replace("\\\"", "\"");
            
            // 追加到完整内容
            fullContent.append(content);
            final String finalContent = content;
            LOG.debug("准备更新UI内容: " + finalContent);
            // 更新UI，确保内容正确显示
            SwingUtilities.invokeLater(() -> {
                LOG.debug("开始执行UI更新");
                dialog.appendContent(finalContent);
                LOG.debug("UI更新完成");
            });
        }
    }

    /**
     * 完成处理
     */
    private void completeProcessing() {
        if (isComplete.compareAndSet(false, true)) {
            dialog.markReviewComplete();
            if (onCompleteCallback != null) {
                onCompleteCallback.accept(fullContent.toString());
            }
        }
    }
}