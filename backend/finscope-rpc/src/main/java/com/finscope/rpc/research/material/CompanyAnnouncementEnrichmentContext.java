package com.finscope.rpc.research.material;

import com.finscope.domain.research.material.ResearchMaterial;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 单轮同步内的列表身份与已验证公司名称，不跨轮次缓存，跟随同步时限释放。 */
public class CompanyAnnouncementEnrichmentContext {
    private final Map<String, ListedIdentity> listings = new HashMap<>();
    private final Map<String, String> issuerNames = new HashMap<>();

    void register(ResearchMaterial material, String market) {
        listings.put(material.getExternalId(), new ListedIdentity(material.getStockCode(), material.getUrl(), market));
    }

    boolean matches(ResearchMaterial material) {
        ListedIdentity identity = listings.get(material.getExternalId());
        return identity != null && Objects.equals(identity.code(), material.getStockCode())
                && Objects.equals(identity.url(), material.getUrl());
    }

    String market(String id) {
        return listings.get(id).market();
    }

    String issuerName(String code) {
        return issuerNames.get(code);
    }

    void rememberIssuer(String code, String name) {
        issuerNames.put(code, name);
    }

    // 不可变副本：调用方修改 material 时，不能一起改写已从列表绑定的身份。
    private record ListedIdentity(String code, String url, String market) {
    }
}
