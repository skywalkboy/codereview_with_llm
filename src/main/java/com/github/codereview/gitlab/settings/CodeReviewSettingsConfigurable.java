package com.github.codereview.gitlab.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * 插件设置界面配置类
 */
public class CodeReviewSettingsConfigurable implements Configurable {
    private JPanel mainPanel;
    private JTextField apiKeyField;
    private JTextField apiUrlField;
    private JCheckBox enableReviewCheckBox;
    private JTextField modelNameField;
    private JSpinner maxTokensSpinner;
    private JSpinner temperatureSpinner;
    private final CodeReviewSettings settings;

    public CodeReviewSettingsConfigurable() {
        settings = CodeReviewSettings.getInstance();
    }

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "GitLab Code Review";
    }

    @Override
    public @Nullable JComponent createComponent() {
        mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 5, 5, 5);

        // API Key
        c.gridx = 0;
        c.gridy = 0;
        mainPanel.add(new JLabel("智谱API密钥:"), c);

        c.gridx = 1;
        c.weightx = 1.0;
        apiKeyField = new JTextField();
        mainPanel.add(apiKeyField, c);

        // API URL
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0.0;
        mainPanel.add(new JLabel("API URL:"), c);

        c.gridx = 1;
        c.weightx = 1.0;
        apiUrlField = new JTextField();
        mainPanel.add(apiUrlField, c);

        // Enable Review
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        enableReviewCheckBox = new JCheckBox("启用代码审查");
        mainPanel.add(enableReviewCheckBox, c);

        // Model Name
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 1;
        c.weightx = 0.0;
        mainPanel.add(new JLabel("模型名称:"), c);

        c.gridx = 1;
        c.weightx = 1.0;
        modelNameField = new JTextField();
        mainPanel.add(modelNameField, c);

        // Max Tokens
        c.gridx = 0;
        c.gridy = 4;
        c.weightx = 0.0;
        mainPanel.add(new JLabel("最大Token数:"), c);

        c.gridx = 1;
        c.weightx = 1.0;
        SpinnerNumberModel maxTokensModel = new SpinnerNumberModel(2000, 100, 10000, 100);
        maxTokensSpinner = new JSpinner(maxTokensModel);
        mainPanel.add(maxTokensSpinner, c);

        // Temperature
        c.gridx = 0;
        c.gridy = 5;
        c.weightx = 0.0;
        mainPanel.add(new JLabel("温度系数:"), c);

        c.gridx = 1;
        c.weightx = 1.0;
        SpinnerNumberModel temperatureModel = new SpinnerNumberModel(0.7, 0.0, 1.0, 0.1);
        temperatureSpinner = new JSpinner(temperatureModel);
        mainPanel.add(temperatureSpinner, c);

        // 填充空白
        c.gridx = 0;
        c.gridy = 6;
        c.gridwidth = 2;
        c.weighty = 1.0;
        mainPanel.add(new JPanel(), c);

        loadSettings();
        return mainPanel;
    }

    private void loadSettings() {
        apiKeyField.setText(settings.getApiKey());
        apiUrlField.setText(settings.getApiUrl());
        enableReviewCheckBox.setSelected(settings.isEnableReview());
        modelNameField.setText(settings.getModelName());
        maxTokensSpinner.setValue(settings.getMaxTokens());
        temperatureSpinner.setValue(settings.getTemperature());
    }

    @Override
    public boolean isModified() {
        return !apiKeyField.getText().equals(settings.getApiKey()) ||
                !apiUrlField.getText().equals(settings.getApiUrl()) ||
                enableReviewCheckBox.isSelected() != settings.isEnableReview() ||
                !modelNameField.getText().equals(settings.getModelName()) ||
                !maxTokensSpinner.getValue().equals(settings.getMaxTokens()) ||
                !temperatureSpinner.getValue().equals(settings.getTemperature());
    }

    @Override
    public void apply() throws ConfigurationException {
        settings.setApiKey(apiKeyField.getText().trim());
        settings.setApiUrl(apiUrlField.getText().trim());
        settings.setEnableReview(enableReviewCheckBox.isSelected());
        settings.setModelName(modelNameField.getText().trim());
        settings.setMaxTokens((Integer) maxTokensSpinner.getValue());
        settings.setTemperature((Double) temperatureSpinner.getValue());
    }

    @Override
    public void reset() {
        loadSettings();
    }
}