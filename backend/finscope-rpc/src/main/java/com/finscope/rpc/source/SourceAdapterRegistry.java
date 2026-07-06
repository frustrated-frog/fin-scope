package com.finscope.rpc.source;

import com.finscope.domain.source.Source;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class SourceAdapterRegistry {
    @Resource
    private List<SourceAdapter> adapters;

    public SourceAdapter get(String type) {
        for (SourceAdapter adapter : adapters) {
            if (adapter.supports(type)) {
                return adapter;
            }
        }
        throw new IllegalArgumentException("Unsupported source type: " + type);
    }

    public SourceAdapter get(Source source) {
        for (SourceAdapter adapter : adapters) {
            if (adapter.supports(source) && !adapter.supports(source.getType()) && !isGenericWebAdapter(adapter)) {
                return adapter;
            }
        }
        for (SourceAdapter adapter : adapters) {
            if (adapter.supports(source.getType())) {
                return adapter;
            }
        }
        for (SourceAdapter adapter : adapters) {
            if (adapter.supports(source)) {
                return adapter;
            }
        }
        throw new IllegalArgumentException("Unsupported source: " + source.getType() + " " + source.getUrl());
    }

    private boolean isGenericWebAdapter(SourceAdapter adapter) {
        return adapter.supports("WEB");
    }
}
