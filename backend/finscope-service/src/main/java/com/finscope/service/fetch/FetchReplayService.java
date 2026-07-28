package com.finscope.service.fetch;

import com.finscope.dao.fetch.RawSnapshotRepository;
import com.finscope.domain.fetch.RawSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FetchReplayService {
    private final RawSnapshotRepository repository;
    private final Path dataRoot;

    public FetchReplayService(RawSnapshotRepository repository,
                              @Value("${finscope.data-root:../data}") String dataRoot) {
        this.repository = repository;
        this.dataRoot = Paths.get(dataRoot);
    }

    public ReplayPayload replay(Long snapshotId) {
        RawSnapshot snapshot = repository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("原始采集快照不存在：" + snapshotId));
        try {
            byte[] bytes = Files.readAllBytes(dataRoot.resolve(snapshot.getBodyPath()));
            Charset charset = Charset.forName(snapshot.getCharsetName() == null ? "UTF-8" : snapshot.getCharsetName());
            return new ReplayPayload(snapshot.getRequestUrl(), snapshot.getContentType(), new String(bytes, charset));
        } catch (Exception error) {
            throw new IllegalStateException("原始采集快照读取失败：" + snapshotId, error);
        }
    }

    public static final class ReplayPayload {
        private final String requestUrl;
        private final String contentType;
        private final String bodyText;

        ReplayPayload(String requestUrl, String contentType, String bodyText) {
            this.requestUrl = requestUrl;
            this.contentType = contentType;
            this.bodyText = bodyText;
        }

        public String getRequestUrl() { return requestUrl; }
        public String getContentType() { return contentType; }
        public String getBodyText() { return bodyText; }
    }
}
