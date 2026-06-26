package com.finscope.service.export;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportService {
    private final Path dataRoot;

    public ExportService(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    public Map<String, Object> exportData() {
        try {
            Files.createDirectories(dataRoot.resolve("exports"));
            String fileName = "backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip";
            Path target = dataRoot.resolve("exports").resolve(fileName);
            String manifest = "{\"product\":\"FinScope\",\"createdAt\":\"" + LocalDateTime.now() + "\"}";
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(target.toFile()))) {
                addFileIfExists(zip, dataRoot.resolve("finance.db"), "finance.db");
                addDirectory(zip, dataRoot.resolve("vault"), "vault/");
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(manifest.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("fileName", fileName);
            result.put("path", target.toString());
            result.put("manifest", manifest);
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to export data", ex);
        }
    }

    private void addFileIfExists(ZipOutputStream zip, Path file, String name) throws Exception {
        if (Files.exists(file) && Files.isRegularFile(file)) {
            zip.putNextEntry(new ZipEntry(name));
            Files.copy(file, zip);
            zip.closeEntry();
        }
    }

    private void addDirectory(ZipOutputStream zip, Path directory, String prefix) throws Exception {
        if (!Files.exists(directory)) {
            return;
        }
        Files.walk(directory)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        String relative = prefix + directory.relativize(path).toString().replace('\\', '/');
                        zip.putNextEntry(new ZipEntry(relative));
                        Files.copy(path, zip);
                        zip.closeEntry();
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                });
    }
}
