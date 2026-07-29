package com.finscope.rpc.research;

import com.finscope.domain.research.ResearchSourceDocument;

public interface ResearchSourceReader {
    ResearchSourceDocument read(String url);
}
