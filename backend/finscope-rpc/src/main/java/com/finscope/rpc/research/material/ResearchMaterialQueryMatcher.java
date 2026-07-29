package com.finscope.rpc.research.material;

/** 结构化资料查询采用关键词析取，避免把自然语言关键词串误当作完整短语。 */
public final class ResearchMaterialQueryMatcher {
    private ResearchMaterialQueryMatcher() {}

    public static boolean matchesAny(String query, String value) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) return true;
        String searchable = value == null ? "" : value;
        String[] tokens = normalizedQuery.split("[\\s,，、;；|/]+");
        for (String token : tokens) {
            String candidate = token.trim();
            if (!candidate.isEmpty() && searchable.contains(candidate)) return true;
        }
        return false;
    }
}
