package com.github.codereview.gitlab.ui;

import com.google.gson.Gson;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;

import javax.swing.*;
import java.awt.*;

/**
 * JCEF浏览器组件，用于渲染Markdown内容
 */
public class JCEFBrowser extends JPanel {
    private final JBCefBrowser browser;
    private final JBCefJSQuery markdownUpdateQuery;
    private final Gson gson = new Gson();
    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        font-size: 12pt;
                        margin: 10px;
                        line-height: 1.5;
                    }
                    pre {
                        background-color: #f6f8fa;
                        border-radius: 6px;
                        padding: 16px;
                        overflow: auto;
                    }
                    code {
                        font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
                        font-size: 12px;
                        background-color: #f6f8fa;
                        padding: 0.2em 0.4em;
                        border-radius: 3px;
                    }
                    blockquote {
                        border-left: 4px solid #dfe2e5;
                        color: #6a737d;
                        margin: 0;
                        padding: 0 1em;
                    }
                    table {
                        border-collapse: collapse;
                        width: 100%;
                        margin: 8px 0;
                    }
                    th, td {
                        border: 1px solid #dfe2e5;
                        padding: 6px 13px;
                    }
                    th {
                        background-color: #f6f8fa;
                    }
                    img {
                        max-width: 100%;
                    }
                </style>
            </head>
            <body>
                <div id="content"></div>
                <script>
                    marked.setOptions({
                        gfm: true,
                        breaks: true,
                        highlight: function(code) {
                            return code;
                        }
                    });
                    
                    function updateMarkdown(markdown) {
                        document.getElementById('content').innerHTML = marked.parse(markdown);
                        window.scrollTo(0, document.body.scrollHeight);
                    }
                </script>
            </body>
            </html>
            """.stripIndent();

    public JCEFBrowser() {
        super(new BorderLayout());
        browser = new JBCefBrowser();
        markdownUpdateQuery = JBCefJSQuery.create(browser);

        // 初始化浏览器
        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                // 页面加载完成后初始化JavaScript桥接
                initJavaScriptBridge();
            }
        }, browser.getCefBrowser());

        // 加载HTML模板
        browser.loadHTML(HTML_TEMPLATE);
        add(browser.getComponent(), BorderLayout.CENTER);
    }

    private void initJavaScriptBridge() {
        // 注册JavaScript查询处理器
        markdownUpdateQuery.addHandler(result -> null);
    }

    /**
     * 更新Markdown内容
     *
     * @param markdown Markdown格式的内容
     */
    public void updateContent(String markdown) {
        if (markdown == null) {
            markdown = "";
        }
        // 转义JavaScript字符串中的特殊字符
        markdown = markdown.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
        // 使用双引号而不是单引号，并确保正确处理多行内容
        String jsCode = "updateMarkdown(" + gson.toJson(markdown) + ");";
        browser.getCefBrowser().executeJavaScript(
                jsCode,
                browser.getCefBrowser().getURL(),
                0
        );
    }

    /**
     * 获取浏览器组件
     *
     * @return 浏览器组件
     */
    public JComponent getComponent() {
        return browser.getComponent();
    }
}