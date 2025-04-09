package com.github.codereview.gitlab.ui;

import com.github.codereview.gitlab.util.StreamingResponseHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import io.netty.util.internal.logging.InternalLogger;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式代码审查结果对话框
 * 支持实时更新Markdown格式的审查结果
 */
public class StreamingReviewDialog extends DialogWrapper {

    private static final Logger LOG  = Logger.getInstance(StreamingReviewDialog.class);;

    private final JCEFBrowser browser;
    private final StringBuilder contentBuilder = new StringBuilder();
    private final AtomicBoolean isReviewComplete = new AtomicBoolean(false);
    private final Project project;
    
    public StreamingReviewDialog(Project project, String title) {
        super(project, false);
        this.project = project;
        setTitle(title);
        
        // 创建JCEF浏览器组件
        browser = new JCEFBrowser();
        init();
        updateContent("正在进行代码审查，请稍候...");
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(800, 600));
        panel.add(browser.getComponent(), BorderLayout.CENTER);
        return panel;
    }
    
    /**
     * 更新对话框内容
     * 
     * @param markdownContent Markdown格式的内容
     */
    public void updateContent(String markdownContent) {
        contentBuilder.setLength(0);
        contentBuilder.append(markdownContent);
        SwingUtilities.invokeLater(() -> {
            browser.updateContent(contentBuilder.toString());
        });
    }
    
    /**
     * 追加内容到对话框
     * 
     * @param markdownChunk Markdown格式的内容片段
     */
    public void appendContent(String markdownChunk) {
        LOG.debug("开始追加内容: " + markdownChunk);
        contentBuilder.append(markdownChunk);
        
        // 使用JCEF浏览器更新内容
        SwingUtilities.invokeLater(() -> {
            LOG.debug("开始更新UI");
            browser.updateContent(contentBuilder.toString());
            LOG.debug("UI更新完成");
        });
    }
    
    /**
     * 标记审查完成
     */
    public void markReviewComplete() {
        isReviewComplete.set(true);
    }
    
    /**
     * 获取当前审查结果内容
     * 
     * @return 当前审查结果内容
     */
    public String getCurrentContent() {
        return contentBuilder.toString();
    }
    
    /**
     * 检查审查是否完成
     * 
     * @return 审查是否完成
     */
    public boolean isReviewComplete() {
        return isReviewComplete.get();
    }
}