package com.org.ai.agents.codeassistant.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSearchTool {

    private final HttpClient httpClient;

    @Value("${agent.web-search.enabled:false}")
    private boolean enabled;

    @Value("${agent.web-search.api-url:https://www.baidu.com/s}")
    private String apiUrl;

    @Value("${agent.web-search.api-key:}")
    private String apiKey;

    public WebSearchTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 搜索互联网内容，返回搜索结果摘要
     */
    @Tool(description = "搜索互联网内容，根据关键词返回搜索结果摘要信息。适用于需要获取最新资讯、技术文档或通用知识的场景。")
    public String webSearch(String query) throws Exception {
        if (!enabled) {
            return "错误：网络搜索功能已被管理员禁用。请在配置文件中设置 agent.web-search.enabled=true";
        }

        // 对查询词进行URL编码
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        // 使用百度搜索
        String searchUrl = apiUrl + "?wd=" + encodedQuery + "&ie=utf-8";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return "搜索请求失败，HTTP状态码：" + response.statusCode();
        }

        // 解析并格式化结果
        String body = response.body();
        return formatSearchResults(query, body);
    }

    /**
     * 对百度搜索结果进行格式化提取
     */
    private String formatSearchResults(String query, String html) {
        StringBuilder sb = new StringBuilder();
        sb.append("【搜索关键词】").append(query).append("\n\n");

        // 提取搜索结果标题和摘要（百度搜索结果结构）
        // 匹配 <div class="result c-container ..."> 中的内容
        Pattern resultPattern = Pattern.compile(
                "<div[^>]*class=\"result[^>]*c-container[^>]*\"[^>]*>(.*?)</div>\\s*</div>\\s*</div>",
                Pattern.DOTALL
        );
        Matcher resultMatcher = resultPattern.matcher(html);

        int count = 0;
        int maxResults = 8;

        while (resultMatcher.find() && count < maxResults) {
            String resultBlock = resultMatcher.group(1);
            count++;

            // 提取标题
            String title = extractHtmlTitle(resultBlock);
            // 提取摘要/描述
            String abstractText = extractHtmlAbstract(resultBlock);
            // 提取链接
            String url = extractHtmlUrl(resultBlock);

            sb.append("【结果").append(count).append("】\n");
            if (!title.isEmpty()) {
                sb.append("标题：").append(title).append("\n");
            }
            if (!url.isEmpty()) {
                sb.append("链接：").append(url).append("\n");
            }
            if (!abstractText.isEmpty()) {
                sb.append("摘要：").append(abstractText).append("\n");
            }
            sb.append("\n");
        }

        // 如果以上正则没有匹配到结果，尝试第二种匹配方式（新版百度搜索结果）
        if (count == 0) {
            count = extractBaiduResultsV2(html, sb, maxResults);
        }

        // 如果仍然没有提取到结果
        if (count == 0) {
            sb.append("【搜索结果摘要】\n");
            // 提取页面中的文本内容（去除HTML标签）
            String textOnly = html.replaceAll("<[^>]+>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            int maxLen = Math.min(textOnly.length(), 1500);
            sb.append(textOnly, 0, maxLen);
        }

        return sb.toString();
    }

    /**
     * 提取百度搜索结果（新版页面结构）
     */
    private int extractBaiduResultsV2(String html, StringBuilder sb, int maxResults) {
        // 新版百度搜索结果的备选匹配模式
        Pattern pattern = Pattern.compile(
                "<div[^>]*class=\"[^\"]*result[^\"]*c-container[^\"]*\"[^>]*>",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(html);

        int count = 0;
        int lastEnd = 0;

        while (matcher.find() && count < maxResults) {
            int start = matcher.start();
            if (lastEnd > 0 && start > lastEnd) {
                // 从上个结果结束到当前结果开始之间找内容
                String contentBlock = html.substring(lastEnd, start);

                String title = extractHtmlTitleV2(contentBlock);
                String url = extractHtmlUrlV2(contentBlock);
                String abstractText = extractHtmlAbstractV2(contentBlock);

                if (!title.isEmpty() || !abstractText.isEmpty()) {
                    count++;
                    sb.append("【结果").append(count).append("】\n");
                    if (!title.isEmpty()) {
                        sb.append("标题：").append(title).append("\n");
                    }
                    if (!url.isEmpty()) {
                        sb.append("链接：").append(url).append("\n");
                    }
                    if (!abstractText.isEmpty()) {
                        sb.append("摘要：").append(abstractText).append("\n");
                    }
                    sb.append("\n");
                }
            }
            lastEnd = matcher.end();
        }

        return count;
    }

    /**
     * 从HTML中提取标题
     */
    private String extractHtmlTitle(String html) {
        // 尝试提取 <a> 标签内的标题
        Pattern p = Pattern.compile("<a[^>]*>(.*?)</a>", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            String title = m.group(1)
                    .replaceAll("<[^>]+>", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (!title.isEmpty()) {
                return title;
            }
        }
        // 尝试提取 h3 标签
        p = Pattern.compile("<h3[^>]*>(.*?)</h3>", Pattern.DOTALL);
        m = p.matcher(html);
        if (m.find()) {
            return m.group(1)
                    .replaceAll("<[^>]+>", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        }
        return "";
    }

    /**
     * 从HTML中提取摘要/描述
     */
    private String extractHtmlAbstract(String html) {
        // 尝试提取 span class="content-right_..." 或 div class="c-abstract"
        Pattern p = Pattern.compile("<span[^>]*class=\"[^\"]*content-right[^\"]*\"[^>]*>(.*?)</span>", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            String text = m.group(1)
                    .replaceAll("<[^>]+>", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (!text.isEmpty()) {
                return text;
            }
        }

        // 尝试提取 c-abstract
        p = Pattern.compile("<[^>]*class=\"[^\"]*c-abstract[^\"]*\"[^>]*>(.*?)</(?:div|span)>", Pattern.DOTALL);
        m = p.matcher(html);
        if (m.find()) {
            return m.group(1)
                    .replaceAll("<[^>]+>", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        // 尝试提取 c-span-last 或 c-span2 中的描述文本
        p = Pattern.compile("<div[^>]*class=\"[^\"]*c-span-last[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL);
        m = p.matcher(html);
        if (m.find()) {
            return m.group(1)
                    .replaceAll("<[^>]+>", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        return "";
    }

    /**
     * 从HTML中提取URL
     */
    private String extractHtmlUrl(String html) {
        Pattern p = Pattern.compile("<a[^>]*href=\"(https?://[^\"]+)\"", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            String url = m.group(1);
            // 过滤掉百度内部链接
            if (!url.contains("baidu.com") || url.contains("baidu.com/link?")) {
                return url;
            }
        }
        return "";
    }

    /**
     * V2版本提取标题
     */
    private String extractHtmlTitleV2(String html) {
        // 匹配 <a> 标签中的文本
        Pattern p = Pattern.compile("<a[^>]*>(.*?)</a>", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            String title = m.group(1)
                    .replaceAll("<[^>]+>", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (!title.isEmpty() && title.length() > 3) {
                return title;
            }
        }
        return "";
    }

    /**
     * V2版本提取URL
     */
    private String extractHtmlUrlV2(String html) {
        Pattern p = Pattern.compile("href=\"(https?://[^\"]+)\"", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    /**
     * V2版本提取摘要
     */
    private String extractHtmlAbstractV2(String html) {
        // 匹配 <div> 中的文本内容
        String text = html.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() > 20) {
            int maxLen = Math.min(text.length(), 300);
            return text.substring(0, maxLen);
        }
        return "";
    }
}
