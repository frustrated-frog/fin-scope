package com.finscope.service.research.evidence;

import com.finscope.domain.research.ResearchSourceDocument;
import com.finscope.rpc.research.ResearchSourceReader;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResearchEvidenceAcquisitionService {
    private static final int MAX_SELECTED_CHUNKS = 4;
    private static final int MAX_SELECTED_CHARS = 3600;
    private final ResearchSourceReader sourceReader;
    private final ResearchEvidenceChunker chunker;
    private final ResearchEvidenceRanker ranker;

    public ResearchEvidenceAcquisitionService(ResearchSourceReader sourceReader, ResearchEvidenceChunker chunker,
                                              ResearchEvidenceRanker ranker) {
        this.sourceReader = sourceReader;
        this.chunker = chunker;
        this.ranker = ranker;
    }

    public ResearchEvidenceAcquisitionResult acquire(String url, String query, String searchSnippet,
                                                     String subject) {
        String snippet = text(searchSnippet);
        try {
            ResearchSourceDocument document = sourceReader.read(url);
            List<ResearchEvidenceChunk> selected = ranker.rank(chunker.chunk(document.getBody()), query,
                    subject, MAX_SELECTED_CHUNKS);
            if (selected.isEmpty()) {
                return fallback(snippet, "NO_RELEVANT_CHUNK", "snippet:fallback:no-relevant-chunk");
            }
            StringBuilder content = new StringBuilder();
            for (ResearchEvidenceChunk chunk : selected) {
                String block = "[S" + (chunk.getIndex() + 1) + "] " + chunk.getText();
                if (content.length() > 0 && content.length() + 2 + block.length() > MAX_SELECTED_CHARS) break;
                if (content.length() > 0) content.append("\n\n");
                content.append(block);
            }
            if (content.length() == 0) {
                return fallback(snippet, "NO_RELEVANT_CHUNK", "snippet:fallback:no-relevant-chunk");
            }
            return new ResearchEvidenceAcquisitionResult(content.toString(), snippet, "FULL_TEXT",
                    document.getExtractionMethod(), document.getFetchStatus(), document.getContentCharCount());
        } catch (Exception error) {
            return fallback(snippet, "FAILED", "snippet:fallback:" + error.getClass().getSimpleName());
        }
    }

    public ResearchEvidenceAcquisitionResult snippetOnly(String snippet) {
        return fallback(text(snippet), "NOT_ATTEMPTED", "snippet:fallback:not-attempted");
    }

    private ResearchEvidenceAcquisitionResult fallback(String snippet, String status, String method) {
        return new ResearchEvidenceAcquisitionResult(snippet, snippet, "SEARCH_SNIPPET", method, status,
                snippet.length());
    }

    private String text(String value) { return value == null ? "" : value.trim(); }
}
