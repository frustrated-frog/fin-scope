package com.finscope.service.fetch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.fetch.RawSnapshotRepository;
import com.finscope.domain.fetch.RawSnapshot;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class RawSnapshotStore {
    private final RawSnapshotRepository repository;
    private final Path dataRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RawSnapshotStore(RawSnapshotRepository repository,
                            @Value("${finscope.data-root:../data}") String dataRoot) {
        this.repository = repository;
        this.dataRoot = Paths.get(dataRoot);
    }

    public RawSnapshot save(AcquisitionRequest request, AcquisitionResponse response,
                            Long fetchRunId, Long sourceId) {
        try {
            LocalDate date = LocalDateTime.ofInstant(response.getFetchedAt(), ZoneId.systemDefault()).toLocalDate();
            String relative = "raw/acquisition/" + date + "/" + response.getBodySha256()
                    + extension(response.getContentType());
            Path target = dataRoot.resolve(relative);
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {
                Files.write(target, response.getBodyBytes());
            }
            RawSnapshot snapshot = new RawSnapshot();
            snapshot.setFetchRunId(fetchRunId);
            snapshot.setSourceId(sourceId);
            snapshot.setPurpose(request.getPurpose());
            snapshot.setMethod(request.getMethod());
            snapshot.setRequestUrl(request.getUri().toString());
            snapshot.setFinalUrl(response.getFinalUri().toString());
            snapshot.setRequestHeadersJson(objectMapper.writeValueAsString(request.auditHeaders()));
            snapshot.setStatus("FETCHED");
            snapshot.setHttpStatus(response.getHttpStatus());
            snapshot.setContentType(response.getContentType());
            snapshot.setCharsetName(response.getCharsetName());
            snapshot.setBodyBytes(response.getBodyBytes().length);
            snapshot.setBodySha256(response.getBodySha256());
            snapshot.setBodyPath(relative);
            snapshot.setAttemptCount(response.getAttemptCount());
            snapshot.setDurationMs(response.getDurationMs());
            snapshot.setPolicyVersion("acquisition-v1");
            snapshot.setParserVersion("pending");
            snapshot.setFetchedAt(LocalDateTime.ofInstant(response.getFetchedAt(), ZoneId.systemDefault()));
            return repository.save(snapshot);
        } catch (Exception error) {
            throw new IllegalStateException("原始采集快照保存失败", error);
        }
    }

    private String extension(String contentType) {
        String value = contentType == null ? "" : contentType.toLowerCase();
        if (value.contains("json")) return ".json";
        if (value.contains("xml") || value.contains("rss") || value.contains("atom")) return ".xml";
        if (value.contains("html")) return ".html";
        return ".bin";
    }
}
