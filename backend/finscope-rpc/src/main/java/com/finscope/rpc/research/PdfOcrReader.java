package com.finscope.rpc.research;

import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.finscope.rpc.marketintel.ProviderContractException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** 使用本机系统 OCR 补读无文本层 PDF，临时文件在成功、失败和超时后均清理。 */
@Component
@Slf4j
public class PdfOcrReader {
    @Value("${finscope.research.pdf-ocr.enabled:true}")
    private boolean enabled = true;
    @Value("${finscope.research.pdf-ocr.timeout-ms:45000}")
    private int timeoutMs = 45000;
    @Value("${finscope.research.pdf-ocr.max-pages:20}")
    private int maxPages = 20;

    public String read(byte[] pdf) {
        if (pdf == null || pdf.length == 0 || pdf.length > 6 * 1024 * 1024) {
            throw new IllegalArgumentException("PDF OCR 输入为空或超过6MiB上限");
        }
        if (!enabled || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")
                || !Files.isExecutable(Path.of("/usr/bin/swift"))) {
            throw new ProviderContractException("PDF_OCR_UNAVAILABLE", "当前环境未提供本地 PDF 文字识别能力", true);
        }
        long remainingMs = Math.min(Math.max(1, timeoutMs), ProviderCallDeadline.remainingMillis());
        if (remainingMs <= 0L) {
            throw new ProviderContractException("TIMEOUT", "PDF 文字识别预算已耗尽", true);
        }
        Path directory = null;
        Process process = null;
        try {
            directory = Files.createTempDirectory("finscope-pdf-ocr-");
            Path source = directory.resolve("input.pdf");
            Path script = directory.resolve("pdf-vision.swift");
            Path output = directory.resolve("body.txt");
            Path errors = directory.resolve("error.txt");
            Files.write(source, pdf);
            try (var resource = new ClassPathResource("ocr/pdf-vision.swift").getInputStream()) {
                Files.copy(resource, script);
            }
            Path moduleCache = Path.of(System.getProperty("java.io.tmpdir"), "finscope-ocr-modules");
            Files.createDirectories(moduleCache);
            process = new ProcessBuilder("/usr/bin/swift", "-module-cache-path", moduleCache.toString(),
                    script.toString(), source.toString(), String.valueOf(Math.max(1, Math.min(50, maxPages))))
                    .redirectOutput(output.toFile()).redirectError(errors.toFile()).start();
            if (!process.waitFor(remainingMs, TimeUnit.MILLISECONDS)) {
                terminate(process);
                process.waitFor(2, TimeUnit.SECONDS);
                throw new ProviderContractException("TIMEOUT", "PDF 本地文字识别超时", true);
            }
            if (process.exitValue() != 0 || Files.size(output) > 1_048_576L) {
                throw new ProviderContractException("PDF_OCR_FAILED", "PDF 本地文字识别未生成可核验正文", true);
            }
            String text = Files.readString(output, StandardCharsets.UTF_8).strip();
            if (text.length() < 20) {
                throw new ProviderContractException("PDF_OCR_FAILED", "PDF 识别正文过短", true);
            }
            return text;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProviderContractException("INTERRUPTED", "PDF 本地文字识别被中断", true, error);
        } catch (IOException error) {
            throw new ProviderContractException("PDF_OCR_UNAVAILABLE", "PDF 本地文字识别进程不可用", true, error);
        } finally {
            if (process != null && process.isAlive()) {
                terminate(process);
            }
            cleanup(directory);
        }
    }

    private void terminate(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private void cleanup(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException error) {
            log.warn("PDF OCR 临时文件清理失败 errorType={}", error.getClass().getSimpleName());
        }
    }
}
