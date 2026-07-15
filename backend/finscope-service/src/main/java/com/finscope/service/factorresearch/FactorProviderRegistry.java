package com.finscope.service.factorresearch;

import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.FactorObservation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FactorProviderRegistry {
    private final Map<FactorIdentity, FactorProvider> byIdentity = new LinkedHashMap<FactorIdentity, FactorProvider>();
    private final Map<String, FactorIdentity> byCode = new LinkedHashMap<String, FactorIdentity>();

    public FactorProviderRegistry(List<FactorProvider> providers) {
        for (FactorProvider provider : providers) {
            for (FactorIdentity identity : provider.factors()) {
                if (byIdentity.putIfAbsent(identity, provider) != null) {
                    throw new IllegalStateException("duplicate factor ownership: " + identity);
                }
                FactorIdentity previous = byCode.putIfAbsent(identity.getCode(), identity);
                if (previous != null && !previous.equals(identity)) {
                    throw new IllegalStateException("ambiguous factor code: " + identity.getCode());
                }
            }
        }
    }

    public FactorProvider provider(FactorIdentity identity) {
        FactorProvider value = byIdentity.get(identity);
        if (value == null) throw new IllegalArgumentException("unknown factor identity: " + identity);
        return value;
    }

    public FactorIdentity identity(String code) {
        FactorIdentity value = byCode.get(code);
        if (value == null) throw new IllegalArgumentException("unknown factor code: " + code);
        return value;
    }

    public boolean contains(String code) { return byCode.containsKey(code); }

    public FactorObservation calculate(String code, FactorCalculationContext context) {
        FactorIdentity identity = identity(code);
        return provider(identity).calculate(context, identity);
    }

    public List<FactorIdentity> factors() { return new ArrayList<FactorIdentity>(byIdentity.keySet()); }

    public static FactorProviderRegistry legacyOnly() {
        return new FactorProviderRegistry(java.util.Collections.<FactorProvider>singletonList(
                new LegacyQuantFactorProvider()));
    }
}
