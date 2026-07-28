package com.finscope.rpc.acquisition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 使用独立无头 Chrome 进程渲染 JavaScript 页面。默认关闭，避免浏览器资源影响普通 HTTP 采集。
 */
@Component
public class HeadlessChromeBrowserFetcher implements BrowserFetcher {
    private static final Logger log = LoggerFactory.getLogger(HeadlessChromeBrowserFetcher.class);
    private final boolean enabled;
    private final String configuredExecutable;
    private final int timeoutMs;
    private final int maxResponseBytes;
    private final BrowserConcurrencyGate concurrencyGate;
    private final List<AcquisitionObserver> observers;

    public HeadlessChromeBrowserFetcher(
            @Value("${finscope.acquisition.browser.enabled:false}") boolean enabled,
            @Value("${finscope.acquisition.browser.executable:}") String configuredExecutable,
            @Value("${finscope.acquisition.browser.timeout-ms:20000}") int timeoutMs,
            @Value("${finscope.acquisition.browser.max-response-bytes:8388608}") int maxResponseBytes,
            @Value("${finscope.acquisition.browser.max-concurrency:1}") int maxConcurrency,
            List<AcquisitionObserver> observers) {
        this.enabled = enabled;
        this.configuredExecutable = configuredExecutable == null ? "" : configuredExecutable.trim();
        this.timeoutMs = timeoutMs;
        this.maxResponseBytes = maxResponseBytes;
        this.concurrencyGate = new BrowserConcurrencyGate(maxConcurrency);
        this.observers = observers == null ? Collections.<AcquisitionObserver>emptyList() : observers;
    }

    @Override
    public AcquisitionResponse fetch(AcquisitionRequest request) {
        if (!enabled) {
            return new DisabledBrowserFetcher().fetch(request);
        }
        int totalTimeoutMs = Math.min(timeoutMs, request.getDeadlineMs());
        long deadlineNanos = System.nanoTime() + totalTimeoutMs * 1_000_000L;
        try (BrowserConcurrencyGate.Permit ignored = concurrencyGate.acquire(totalTimeoutMs)) {
            return fetchWithPermit(request, remainingMillis(deadlineNanos));
        }
    }

    private AcquisitionResponse fetchWithPermit(AcquisitionRequest request, int executionTimeoutMs) {
        long startedNanos = System.nanoTime();
        Path profile = null;
        Process process = null;
        try {
            profile = Files.createTempDirectory("finscope-browser-");
            List<String> command = command(request, profile);
            process = new ProcessBuilder(command).start();
            StreamCollector output = new StreamCollector(process.getInputStream(), maxResponseBytes);
            StreamCollector errors = new StreamCollector(process.getErrorStream(), 64 * 1024);
            Thread outputThread = collectorThread(output, "finscope-browser-output");
            Thread errorThread = collectorThread(errors, "finscope-browser-error");
            outputThread.start();
            errorThread.start();

            if (!process.waitFor(executionTimeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new AcquisitionException(AcquisitionErrorType.TIMEOUT,
                        "浏览器渲染超过时间限制：" + executionTimeoutMs + " ms", true, null);
            }
            outputThread.join(1000L);
            errorThread.join(1000L);
            if (output.isOverflow()) {
                throw new AcquisitionException(AcquisitionErrorType.RESPONSE_TOO_LARGE,
                        "浏览器渲染响应超过大小限制：" + maxResponseBytes + " bytes", false, null);
            }
            byte[] bytes = output.bytes();
            if (process.exitValue() != 0 || bytes.length == 0) {
                throw new AcquisitionException(AcquisitionErrorType.RENDER_REQUIRED,
                        "浏览器渲染失败：" + errors.text(), false, null);
            }
            String html = new String(bytes, StandardCharsets.UTF_8);
            AcquisitionResponse response = new AcquisitionResponse(
                    request.getUri(), request.getUri(), 200,
                    Collections.<String, String>emptyMap(), bytes, html,
                    "text/html; charset=utf-8", "UTF-8", sha256(bytes), 1,
                    elapsedMillis(startedNanos), Instant.now());
            notifySuccess(request, response);
            return response;
        } catch (AcquisitionException error) {
            throw error;
        } catch (IOException error) {
            throw new AcquisitionException(AcquisitionErrorType.BROWSER_UNAVAILABLE,
                    "无法启动无头浏览器，请检查 executable 配置", false, null, error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AcquisitionException(AcquisitionErrorType.CONNECTION_ERROR,
                    "浏览器渲染被中断", false, null, error);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteProfile(profile);
        }
    }

    private List<String> command(AcquisitionRequest request, Path profile) {
        List<String> command = new ArrayList<String>();
        command.add(resolveExecutable());
        command.add("--headless=new");
        command.add("--disable-gpu");
        command.add("--disable-background-networking");
        command.add("--disable-component-update");
        command.add("--no-first-run");
        command.add("--no-default-browser-check");
        command.add("--user-data-dir=" + profile.toAbsolutePath());
        command.add("--dump-dom");
        command.add(request.getUri().toString());
        return command;
    }

    private String resolveExecutable() {
        if (!configuredExecutable.isEmpty()) {
            return configuredExecutable;
        }
        String macChrome = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
        if (Files.isExecutable(Paths.get(macChrome))) {
            return macChrome;
        }
        String macChromium = "/Applications/Chromium.app/Contents/MacOS/Chromium";
        if (Files.isExecutable(Paths.get(macChromium))) {
            return macChromium;
        }
        return "google-chrome";
    }

    private Thread collectorThread(StreamCollector collector, String name) {
        Thread thread = new Thread(collector, name);
        thread.setDaemon(true);
        return thread;
    }

    private void notifySuccess(AcquisitionRequest request, AcquisitionResponse response) {
        for (AcquisitionObserver observer : observers) {
            try {
                observer.onSuccess(request, response);
            } catch (RuntimeException error) {
                log.warn("浏览器采集观察器执行失败 purpose={} url={}", request.getPurpose(), request.getUri());
            }
        }
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder();
            for (byte item : digest) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (Exception error) {
            throw new IllegalStateException("无法计算浏览器响应哈希", error);
        }
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private int remainingMillis(long deadlineNanos) {
        long remaining = (deadlineNanos - System.nanoTime()) / 1_000_000L;
        if (remaining <= 0L) {
            throw new AcquisitionException(AcquisitionErrorType.TIMEOUT,
                    "浏览器采集总时间预算已耗尽", true, null);
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, remaining));
    }

    private void deleteProfile(Path profile) {
        if (profile == null || !Files.exists(profile)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(profile)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时浏览器目录由系统后续清理。
                }
            });
        } catch (IOException ignored) {
            // 临时浏览器目录由系统后续清理。
        }
    }

    private static final class StreamCollector implements Runnable {
        private final InputStream input;
        private final int limit;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private volatile boolean overflow;

        private StreamCollector(InputStream input, int limit) {
            this.input = input;
            this.limit = limit;
        }

        @Override
        public void run() {
            try (InputStream stream = input) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    int remaining = limit - output.size();
                    if (remaining > 0) {
                        output.write(buffer, 0, Math.min(read, remaining));
                    }
                    if (read > remaining) {
                        overflow = true;
                    }
                }
            } catch (IOException ignored) {
                // 进程退出时流可能关闭，调用方依据退出码与正文判断结果。
            }
        }

        private boolean isOverflow() {
            return overflow;
        }

        private byte[] bytes() {
            return output.toByteArray();
        }

        private String text() {
            String value = new String(bytes(), StandardCharsets.UTF_8).trim();
            return value.length() <= 300 ? value : value.substring(0, 300);
        }
    }
}
