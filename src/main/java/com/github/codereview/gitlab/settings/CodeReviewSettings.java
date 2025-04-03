package com.github.codereview.gitlab.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 存储插件设置的持久化组件
 */
@State(
    name = "com.github.codereview.gitlab.settings.CodeReviewSettings",
    storages = {@Storage("GitLabCodeReviewSettings.xml")}
)
public class CodeReviewSettings implements PersistentStateComponent<CodeReviewSettings> {
    private String apiKey = "90ef573bb96b4434a012d5b8c6389192.TBSHG61OF0t0AB05";
    private String apiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private boolean enableReview = true;
    private String modelName = "glm-4";
    private int maxTokens = 2000;
    private double temperature = 0.7;
    private String webhookUrl = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=4ac05f9b-553c-40cc-8120-d9d5553c39df";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public boolean isEnableReview() {
        return enableReview;
    }

    public void setEnableReview(boolean enableReview) {
        this.enableReview = enableReview;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override
    public @Nullable CodeReviewSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull CodeReviewSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static CodeReviewSettings getInstance() {
        return ApplicationManager.getApplication().getService(CodeReviewSettings.class);
    }
}