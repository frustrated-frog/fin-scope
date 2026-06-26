package com.finscope.service.vault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
}
