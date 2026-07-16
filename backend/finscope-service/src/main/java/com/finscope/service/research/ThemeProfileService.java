package com.finscope.service.research;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ThemeProfile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ThemeProfileService {
    private final Map<String, ThemeProfile> registry = new LinkedHashMap<String, ThemeProfile>();

    public ThemeProfileService() {
        register(theme(
                ResearchEnums.THEME_AI_STARTUP,
                "AI 创业与产品",
                "追踪 AI 产品发布、创业公司与开发者生态变化",
                "AI 与创业",
                Arrays.asList("CURATED_AI", ResearchEnums.SOURCE_TIER_COMPANY, ResearchEnums.SOURCE_TIER_MEDIA),
                Arrays.asList(ResearchEnums.CONTENT_FORMAT_PODCAST, ResearchEnums.CONTENT_FORMAT_X_THREAD)));
        register(theme(
                ResearchEnums.THEME_CHINA_MACRO,
                "中国宏观与政策",
                "追踪宏观政策、利率预期与资产价格传导",
                "宏观与市场",
                Arrays.asList(ResearchEnums.SOURCE_TIER_REGULATOR, ResearchEnums.SOURCE_TIER_OFFICIAL, ResearchEnums.SOURCE_TIER_MEDIA),
                Arrays.asList(ResearchEnums.CONTENT_FORMAT_LONG_ARTICLE, ResearchEnums.CONTENT_FORMAT_X_THREAD)));
        register(theme(
                ResearchEnums.THEME_COMPANY_IPO,
                "公司与 IPO",
                "追踪公司融资、上市和财报相关事件",
                "公司与产业",
                Arrays.asList(ResearchEnums.SOURCE_TIER_COMPANY, ResearchEnums.SOURCE_TIER_MEDIA),
                Arrays.asList(ResearchEnums.CONTENT_FORMAT_LONG_ARTICLE, ResearchEnums.CONTENT_FORMAT_XIAOHONGSHU_NOTE)));
    }

    public List<ThemeProfile> findAll() {
        return new ArrayList<ThemeProfile>(registry.values());
    }

    public ThemeProfile getRequired(String code) {
        ThemeProfile profile = registry.get(code);
        if (profile == null) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "Unknown theme code: " + code);
        }
        return profile;
    }

    public List<ThemeProfile> getRequired(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "At least one theme code is required");
        }
        List<ThemeProfile> profiles = new ArrayList<ThemeProfile>();
        for (String code : codes) {
            profiles.add(getRequired(code));
        }
        return profiles;
    }

    private void register(ThemeProfile profile) {
        registry.put(profile.getCode(), profile);
    }

    private ThemeProfile theme(String code,
                               String name,
                               String description,
                               String briefSection,
                               List<String> preferredTiers,
                               List<String> preferredFormats) {
        ThemeProfile profile = new ThemeProfile();
        profile.setCode(code);
        profile.setName(name);
        profile.setDescription(description);
        profile.setBriefSection(briefSection);
        profile.setPreferredTiers(preferredTiers);
        profile.setPreferredFormats(preferredFormats);
        profile.setCreatorEnabled(true);
        return profile;
    }
}
