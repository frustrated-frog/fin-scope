package com.finscope.rpc.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * HTML to Markdown 转换器
 *
 * <p>高级特性：
 * <ul>
 *   <li>智能内容提取 - 自动识别主要内容区域</li>
 *   <li>保留语义结构 - 标题层级、列表嵌套、表格格式</li>
 *   <li>链接处理 - 相对路径转绝对路径</li>
 *   <li>代码块优化 - 自动识别编程语言</li>
 *   <li>性能优化 - 避免重复解析</li>
 * </ul>
 *
 * @author FinScope Team
 * @since 1.0.0
 */
public final class HtmlToMarkdownConverter {

    private static final Logger logger = LoggerFactory.getLogger(HtmlToMarkdownConverter.class);

    // 内容选择器优先级（从高到低）
    private static final String[] CONTENT_SELECTORS = {
        "article", "main", "[role='main']", ".article-content",
        ".post-content", ".entry-content", ".content", "#content"
    };

    // 多余空白行模式
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n{3,}");

    // 私有构造函数，防止实例化
    private HtmlToMarkdownConverter() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * 将HTML转换为Markdown格式
     *
     * @param html HTML内容
     * @return Markdown格式文本，如果输入为空则返回空字符串
     */
    public static String convert(String html) {
        return convert(html, ConversionConfig.defaultConfig());
    }

    /**
     * 使用自定义配置转换HTML为Markdown
     *
     * @param html HTML内容
     * @param config 转换配置
     * @return Markdown格式文本
     */
    public static String convert(String html, ConversionConfig config) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }

        try {
            Document doc = Jsoup.parse(html);

            // 设置base URI用于解析相对路径
            if (config.getBaseUri() != null && !config.getBaseUri().isEmpty()) {
                doc.setBaseUri(config.getBaseUri());
            }

            Element contentRoot = extractMainContent(doc);

            MarkdownBuilder builder = new MarkdownBuilder(config);
            traverseNode(contentRoot, builder, new ArrayDeque<>());

            return postProcess(builder.build());
        } catch (Exception e) {
            logger.error("转换 HTML 为 Markdown 失败", e);
            return fallbackExtraction(html);
        }
    }

    /**
     * 提取主要内容区域
     */
    private static Element extractMainContent(Document doc) {
        // 尝试按优先级查找主要内容区域
        for (String selector : CONTENT_SELECTORS) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                Element element = elements.first();
                if (hasSignificantContent(element)) {
                    logger.debug("使用选择器找到正文 selector={}", selector);
                    return element;
                }
            }
        }

        // 降级到body
        Element body = doc.body();
        return body != null ? body : doc;
    }

    /**
     * 判断元素是否有实质性内容
     */
    private static boolean hasSignificantContent(Element element) {
        String text = element.text().trim();
        return text.length() > 100; // 至少100字符才算有实质内容
    }

    /**
     * 递归遍历节点并转换为Markdown
     */
    private static void traverseNode(Node node, MarkdownBuilder builder, Deque<ListContext> listStack) {
        if (node instanceof TextNode) {
            TextNode textNode = (TextNode) node;
            String text = textNode.text();
            if (!text.isEmpty()) {
                builder.append(text);
            }
        } else if (node instanceof Element) {
            Element element = (Element) node;
            processElement(element, builder, listStack);
        }
    }

    /**
     * 处理HTML元素
     */
    private static void processElement(Element element, MarkdownBuilder builder, Deque<ListContext> listStack) {
        String tagName = element.tagName().toLowerCase();

        switch (tagName) {
            // 标题
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
                processHeading(element, builder);
                break;

            // 段落和换行
            case "p":
                processParagraph(element, builder, listStack);
                break;
            case "br":
                builder.append("\n");
                break;

            // 文本格式
            case "strong":
            case "b":
                processBold(element, builder, listStack);
                break;
            case "em":
            case "i":
                processItalic(element, builder, listStack);
                break;
            case "code":
                processInlineCode(element, builder, listStack);
                break;
            case "pre":
                processCodeBlock(element, builder);
                break;

            // 引用
            case "blockquote":
                processBlockquote(element, builder, listStack);
                break;

            // 链接和图片
            case "a":
                processLink(element, builder, listStack);
                break;
            case "img":
                processImage(element, builder);
                break;

            // 列表
            case "ul":
            case "ol":
                processList(element, tagName.equals("ol"), builder, listStack);
                break;
            case "li":
                processListItem(element, builder, listStack);
                break;

            // 表格
            case "table":
                processTable(element, builder, listStack);
                break;

            // 分隔线
            case "hr":
                builder.append("\n---\n\n");
                break;

            // 容器元素 - 继续处理子元素
            case "div":
            case "section":
            case "article":
            case "main":
            case "span":
            case "header":
            case "footer":
            case "nav":
            case "aside":
                traverseChildren(element, builder, listStack);
                break;

            // 其他元素 - 默认处理子元素
            default:
                traverseChildren(element, builder, listStack);
                break;
        }
    }

    /**
     * 处理标题
     */
    private static void processHeading(Element element, MarkdownBuilder builder) {
        int level = Integer.parseInt(element.tagName().substring(1));
        StringBuilder prefixBuilder = new StringBuilder();
        for (int i = 0; i < level; i++) {
            prefixBuilder.append('#');
        }
        String prefix = prefixBuilder.toString();
        String content = element.text().trim();

        builder.ensureNewline()
                .append(prefix).append(" ")
                .append(content)
                .append("\n\n");
    }

    /**
     * 处理段落
     */
    private static void processParagraph(Element element, MarkdownBuilder builder, Deque<ListContext> listStack) {
        builder.ensureNewline();
        traverseChildren(element, builder, listStack);
        builder.append("\n\n");
    }

    /**
     * 处理粗体
     */
    private static void processBold(Element element, MarkdownBuilder builder, Deque<ListContext> listStack) {
        builder.append("**");
        traverseChildren(element, builder, listStack);
        builder.append("**");
    }

    /**
     * 处理斜体
     */
    private static void processItalic(Element element, MarkdownBuilder builder, Deque<ListContext> listStack) {
        builder.append("*");
        traverseChildren(element, builder, listStack);
        builder.append("*");
    }

    /**
     * 处理行内代码
     */
    private static void processInlineCode(Element element, MarkdownBuilder builder, Deque<ListContext> listStack) {
        builder.append("`");
        traverseChildren(element, builder, listStack);
        builder.append("`");
    }

    /**
     * 处理代码块
     */
    private static void processCodeBlock(Element element, MarkdownBuilder builder) {
        // 尝试识别编程语言
        String language = detectLanguage(element);
        String code = element.text();

        builder.ensureNewline()
                .append("```").append(language).append("\n")
                .append(code)
                .append("\n```\n\n");
    }

    /**
     * 检测代码语言
     */
    private static String detectLanguage(Element element) {
        // 从class属性中提取语言信息
        String className = element.className();
        if (className.contains("language-")) {
            return className.replaceAll(".*language-(\\w+).*", "$1");
        }

        // 从子元素中查找
        Element codeElement = element.selectFirst("code");
        if (codeElement != null) {
            String codeClass = codeElement.className();
            if (codeClass.contains("language-")) {
                return codeClass.replaceAll(".*language-(\\w+).*", "$1");
            }
        }

        return ""; // 默认无语言标识
    }

    /**
     * 处理引用块
     */
    private static void processBlockquote(Element element, MarkdownBuilder builder, Deque<ListContext> listStack) {
        builder.ensureNewline();

        MarkdownBuilder innerBuilder = new MarkdownBuilder(builder.getConfig());
        traverseChildren(element, innerBuilder, listStack);

        String[] lines = innerBuilder.build().split("\n");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                builder.append("> ").append(line).append("\n");
            }
        }
        builder.append("\n");
    }

    /**
     * 处理链接
     */
    private static void processLink(Element element, MarkdownBuilder builder, Deque<ListContext> listStack) {
        String href = element.attr("href").trim();
        String text = element.text().trim();

        if (href.isEmpty()) {
            // 没有链接地址，只显示文本
            builder.append(text);
        } else {
            // 将相对路径转换为绝对路径
            String absoluteUrl = element.absUrl("href");
            String finalUrl = absoluteUrl.isEmpty() ? href : absoluteUrl;

            builder.append("[").append(text).append("](").append(finalUrl).append(")");
        }
    }

    /**
     * 处理图片
     */
    private static void processImage(Element element, MarkdownBuilder builder) {
        String src = element.attr("src").trim();
        String alt = element.attr("alt").trim();

        if (!src.isEmpty()) {
            // 将相对路径转换为绝对路径
            String absoluteUrl = element.absUrl("src");
            String finalUrl = absoluteUrl.isEmpty() ? src : absoluteUrl;

            builder.append("![").append(alt.isEmpty() ? "image" : alt).append("](").append(finalUrl).append(")");
        }
    }

    /**
     * 处理列表
     */
    private static void processList(Element element, boolean ordered, MarkdownBuilder builder, Deque<ListContext> listStack) {
        listStack.push(new ListContext(ordered, 0));

        builder.ensureNewline();
        traverseChildren(element, builder, listStack);
        builder.append("\n");

        listStack.pop();
    }

    /**
     * 处理列表项
     */
    private static void processListItem(Element element, MarkdownBuilder builder, Deque<ListContext> listStack) {
        if (listStack.isEmpty()) {
            // 没有列表上下文，当作无序列表处理
            builder.append("- ");
            traverseChildren(element, builder, listStack);
            builder.append("\n");
            return;
        }

        ListContext context = listStack.peek();
        context.increment();

        // 生成缩进
        StringBuilder indentBuilder = new StringBuilder();
        for (int i = 0; i < listStack.size() - 1; i++) {
            indentBuilder.append("  ");
        }
        String indent = indentBuilder.toString();

        // 生成列表标记
        String marker = context.isOrdered()
                ? context.getCurrentIndex() + ". "
                : "- ";

        builder.append(indent).append(marker);
        traverseChildren(element, builder, listStack);
        builder.append("\n");
    }

    /**
     * 处理表格
     */
    private static void processTable(Element element, MarkdownBuilder builder, Deque<ListContext> listStack) {
        Elements rows = element.select("tr");
        if (rows.isEmpty()) {
            return;
        }

        builder.ensureNewline();

        // 处理表头
        Element headerRow = rows.first();
        Elements headers = headerRow.select("th, td");

        if (!headers.isEmpty()) {
            // 表头行
            builder.append("|");
            for (Element header : headers) {
                String content = header.text().trim();
                builder.append(" ").append(content).append(" |");
            }
            builder.append("\n");

            // 分隔行
            builder.append("|");
            for (int i = 0; i < headers.size(); i++) {
                builder.append(" --- |");
            }
            builder.append("\n");

            // 数据行
            for (int i = 1; i < rows.size(); i++) {
                Element row = rows.get(i);
                Elements cells = row.select("td");

                builder.append("|");
                for (Element cell : cells) {
                    String content = cell.text().trim();
                    builder.append(" ").append(content).append(" |");
                }
                builder.append("\n");
            }

            builder.append("\n");
        }
    }

    /**
     * 遍历子节点
     */
    private static void traverseChildren(Element element, MarkdownBuilder builder, Deque<ListContext> listStack) {
        for (Node child : element.childNodes()) {
            traverseNode(child, builder, listStack);
        }
    }

    /**
     * 后处理 - 清理多余的空白行和空格
     */
    private static String postProcess(String markdown) {
        // 替换多个连续空行为两个空行
        markdown = MULTIPLE_NEWLINES.matcher(markdown).replaceAll("\n\n");
        // 去除首尾空白
        return markdown.trim();
    }

    /**
     * 降级提取 - 当解析失败时使用简单文本提取
     */
    private static String fallbackExtraction(String html) {
        try {
            Document doc = Jsoup.parse(html);
            return doc.text();
        } catch (Exception e) {
            logger.error("降级提取也失败", e);
            return "";
        }
    }

    /**
     * Markdown构建器
     */
    private static class MarkdownBuilder {
        private final StringBuilder content;
        private final ConversionConfig config;

        public MarkdownBuilder(ConversionConfig config) {
            this.content = new StringBuilder(1024);
            this.config = Objects.requireNonNull(config);
        }

        public ConversionConfig getConfig() {
            return config;
        }

        public MarkdownBuilder append(String text) {
            content.append(text);
            return this;
        }

        public MarkdownBuilder append(char ch) {
            content.append(ch);
            return this;
        }

        /**
         * 确保前面有换行
         */
        public MarkdownBuilder ensureNewline() {
            if (content.length() > 0) {
                char lastChar = content.charAt(content.length() - 1);
                if (lastChar != '\n') {
                    content.append('\n');
                }
            }
            return this;
        }

        public String build() {
            return content.toString();
        }
    }

    /**
     * 列表上下文
     */
    private static class ListContext {
        private final boolean ordered;
        private int currentIndex;

        public ListContext(boolean ordered, int startIndex) {
            this.ordered = ordered;
            this.currentIndex = startIndex;
        }

        public boolean isOrdered() {
            return ordered;
        }

        public void increment() {
            currentIndex++;
        }

        public int getCurrentIndex() {
            return currentIndex;
        }
    }

    /**
     * 转换配置
     */
    public static class ConversionConfig {
        private final boolean preserveLinks;
        private final boolean preserveImages;
        private final boolean detectLanguage;
        private final String baseUri;

        private ConversionConfig(Builder builder) {
            this.preserveLinks = builder.preserveLinks;
            this.preserveImages = builder.preserveImages;
            this.detectLanguage = builder.detectLanguage;
            this.baseUri = builder.baseUri;
        }

        public static ConversionConfig defaultConfig() {
            return new Builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public boolean shouldPreserveLinks() {
            return preserveLinks;
        }

        public boolean shouldPreserveImages() {
            return preserveImages;
        }

        public boolean shouldDetectLanguage() {
            return detectLanguage;
        }

        public String getBaseUri() {
            return baseUri;
        }

        public static class Builder {
            private boolean preserveLinks = true;
            private boolean preserveImages = true;
            private boolean detectLanguage = true;
            private String baseUri = "";

            public Builder preserveLinks(boolean preserve) {
                this.preserveLinks = preserve;
                return this;
            }

            public Builder preserveImages(boolean preserve) {
                this.preserveImages = preserve;
                return this;
            }

            public Builder detectLanguage(boolean detect) {
                this.detectLanguage = detect;
                return this;
            }

            public Builder baseUri(String uri) {
                this.baseUri = uri;
                return this;
            }

            public ConversionConfig build() {
                return new ConversionConfig(this);
            }
        }
    }
}
