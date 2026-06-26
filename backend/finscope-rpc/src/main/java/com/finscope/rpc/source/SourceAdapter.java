package com.finscope.rpc.source;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;

import java.util.List;

public interface SourceAdapter {
    boolean supports(String type);

    default boolean supports(Source source) {
        return source != null && supports(source.getType());
    }

    List<RawItem> fetch(Source source) throws Exception;
}
