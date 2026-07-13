package com.finscope.service.vault;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class VaultWriter {
    private final Path vaultRoot;

    public VaultWriter(Path vaultRoot) {
        this.vaultRoot = vaultRoot;
    }

    public Path writeDailyBrief(LocalDate date, String markdown) throws IOException {
        Path directory = vaultRoot.resolve("daily-briefs");
        Files.createDirectories(directory);
        Path target = directory.resolve(date.toString() + ".md");
        Files.write(target, markdown.getBytes(StandardCharsets.UTF_8));
        return target;
    }

    public Path writeResearchReport(Long thesisId, Long researchRunId, String markdown) throws IOException {
        if (researchRunId == null) {
            throw new IllegalArgumentException("Research run id is required");
        }
        String thesisDirectory = thesisId == null ? "standalone" : "thesis-" + thesisId;
        Path directory = vaultRoot.resolve("research-reports").resolve(thesisDirectory);
        Files.createDirectories(directory);
        Path target = directory.resolve("run-" + researchRunId + ".md");
        Path temporary = Files.createTempFile(directory, "run-" + researchRunId + "-", ".tmp");
        try {
            Files.write(temporary, (markdown == null ? "" : markdown).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }

    public List<Path> listDailyBriefs() throws IOException {
        Path directory = vaultRoot.resolve("daily-briefs");
        if (!Files.exists(directory)) {
            return new ArrayList<>();
        }
        List<Path> briefs = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .forEach(briefs::add);
        }
        return briefs;
    }

    public String readDailyBrief(LocalDate date) throws IOException {
        Path target = dailyBriefPath(date);
        if (!Files.exists(target)) {
            return "";
        }
        return new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
    }

    public Path dailyBriefPath(LocalDate date) {
        return vaultRoot.resolve("daily-briefs").resolve(date.toString() + ".md");
    }

    public Path writeTopic(String slug, String markdown) throws IOException {
        Path directory = vaultRoot.resolve("topics");
        Files.createDirectories(directory);
        Path target = directory.resolve(slug + ".md");
        Files.write(target, markdown.getBytes(StandardCharsets.UTF_8));
        return target;
    }

    public String readTopic(String slug) throws IOException {
        Path target = vaultRoot.resolve("topics").resolve(slug + ".md");
        if (!Files.exists(target)) {
            return "";
        }
        return new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
    }

    public Path appendTopicNote(String slug, String note) throws IOException {
        Path directory = vaultRoot.resolve("topics");
        Files.createDirectories(directory);
        Path target = directory.resolve(slug + ".md");
        String existing = Files.exists(target) ? new String(Files.readAllBytes(target), StandardCharsets.UTF_8) : "";
        StringBuilder markdown = new StringBuilder(existing);
        if (markdown.length() > 0 && markdown.charAt(markdown.length() - 1) != '\n') {
            markdown.append("\n");
        }
        markdown.append("\n## 学习笔记\n\n");
        markdown.append("- ").append(LocalDateTime.now()).append(" ").append(note == null ? "" : note).append("\n");
        Files.write(target, markdown.toString().getBytes(StandardCharsets.UTF_8));
        return target;
    }

    public synchronized Path appendKnowledgeEntry(String slug, long entryId, String markdownBlock)
            throws IOException {
        Path directory = vaultRoot.resolve("topics");
        Files.createDirectories(directory);
        Path target = directory.resolve(slug + ".md");
        String existing = Files.exists(target)
                ? new String(Files.readAllBytes(target), StandardCharsets.UTF_8)
                : "";
        String marker = "<!-- knowledge-entry:" + entryId + " -->";
        if (existing.contains(marker)) {
            return target;
        }

        StringBuilder next = new StringBuilder(existing);
        if (next.length() > 0 && next.charAt(next.length() - 1) != '\n') {
            next.append('\n');
        }
        next.append('\n');
        if (markdownBlock == null || !markdownBlock.contains(marker)) {
            next.append(marker).append('\n');
        }
        next.append(markdownBlock == null ? "" : markdownBlock);
        if (next.length() == 0 || next.charAt(next.length() - 1) != '\n') {
            next.append('\n');
        }

        Path temporary = Files.createTempFile(directory, slug + "-", ".tmp");
        try {
            Files.write(temporary, next.toString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }
}
