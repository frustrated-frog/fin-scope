package com.finscope.common.constant;

/**
 * 内容相关常量
 */
public final class ContentConstants {

    // 默认值
    public static final String DEFAULT_ARTICLE_TITLE = "未命名文章";
    public static final String DEFAULT_CATEGORY = "市场";
    public static final String DEFAULT_SOURCE_NAME = "手动研究";

    // 分类
    public static final String CATEGORY_MACRO = "宏观";
    public static final String CATEGORY_POLICY = "政策";
    public static final String CATEGORY_COMPANY = "公司";
    public static final String CATEGORY_INDUSTRY = "行业";
    public static final String CATEGORY_MARKET = "市场";
    public static final String CATEGORY_TECH = "科技";

    // 新意类型
    public static final String NOVELTY_NEW = "NEW";
    public static final String NOVELTY_DUPLICATE = "DUPLICATE";
    public static final String NOVELTY_FOLLOW_UP = "FOLLOW_UP";

    // 内容类型
    public static final String CONTENT_TYPE_FINANCIAL = "FINANCIAL";
    public static final String CONTENT_TYPE_PAPER = "PAPER";
    public static final String CONTENT_TYPE_SOCIAL = "SOCIAL";
    public static final String CONTENT_TYPE_GENERAL = "GENERAL";

    // 主题状态
    public static final String TOPIC_STATUS_LEARNING = "LEARNING";
    public static final String TOPIC_STATUS_REVIEWING = "REVIEWING";
    public static final String TOPIC_STATUS_MATURE = "MATURE";

    // Agent节点名称
    public static final String AGENT_NODE_ARTICLE_INTERPRET = "article-interpret";

    /**
     * 私有构造函数防止实例化
     */
    private ContentConstants() {
        throw new UnsupportedOperationException("Constant class");
    }
}
