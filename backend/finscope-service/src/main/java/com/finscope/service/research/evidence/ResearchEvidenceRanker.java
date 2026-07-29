package com.finscope.service.research.evidence;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ResearchEvidenceRanker {
    public List<ResearchEvidenceChunk> rank(List<ResearchEvidenceChunk> chunks, String query,
                                            String subject, int limit) {
        if (chunks == null || chunks.isEmpty() || limit <= 0) return Collections.emptyList();
        Set<String> terms = terms((query == null ? "" : query) + " " + (subject == null ? "" : subject));
        List<ScoredChunk> scored = new ArrayList<ScoredChunk>();
        for (ResearchEvidenceChunk chunk : chunks) {
            String text = normalize(chunk.getText());
            int score = 0;
            for (String term : terms) {
                if (text.contains(term)) score += term.length() >= 4 ? 4 : 2;
            }
            score += numberCount(text);
            if (score > 0) scored.add(new ScoredChunk(chunk, score));
        }
        scored.sort(Comparator.comparingInt(ScoredChunk::getScore).reversed()
                .thenComparingInt(item -> item.getChunk().getIndex()));
        List<ResearchEvidenceChunk> result = new ArrayList<ResearchEvidenceChunk>();
        for (ScoredChunk item : scored) {
            result.add(item.getChunk());
            if (result.size() == limit) break;
        }
        result.sort(Comparator.comparingInt(ResearchEvidenceChunk::getIndex));
        return result;
    }

    private Set<String> terms(String value) {
        Set<String> result = new HashSet<String>();
        for (String token : normalize(value).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 2) {
                result.add(token);
                for (int size = 2; size <= Math.min(4, token.length()); size++) {
                    for (int index = 0; index + size <= token.length(); index++) {
                        result.add(token.substring(index, index + size));
                    }
                }
            }
        }
        return result;
    }

    private int numberCount(String value) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (Character.isDigit(value.charAt(index))) count++;
        }
        return Math.min(5, count);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static final class ScoredChunk {
        private final ResearchEvidenceChunk chunk;
        private final int score;

        private ScoredChunk(ResearchEvidenceChunk chunk, int score) {
            this.chunk = chunk;
            this.score = score;
        }

        private ResearchEvidenceChunk getChunk() { return chunk; }
        private int getScore() { return score; }
    }
}
