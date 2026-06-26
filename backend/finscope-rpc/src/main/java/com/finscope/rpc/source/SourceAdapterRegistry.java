package com.finscope.rpc.source;

import com.finscope.domain.source.Source;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SourceAdapterRegistry {


    private final List<SourceAdapter> adapters;

    public SourceAdapterRegistry(List<SourceAdapter> adapters) {
        this.adapters = adapters;
    }

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
            if (adapter.supports(source) && !adapter.supports(source.getType())) {
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
}
