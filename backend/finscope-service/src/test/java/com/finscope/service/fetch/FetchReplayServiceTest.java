package com.finscope.service.fetch;

import com.finscope.dao.fetch.RawSnapshotRepository;
import com.finscope.domain.fetch.RawSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FetchReplayServiceTest {

    @Test
    void loadsSnapshotBodyWithoutNetworkAccess(@TempDir Path root) throws Exception {
        Path body = root.resolve("raw/acquisition/2026-07-28/hash.html");
        Files.createDirectories(body.getParent());
        Files.write(body, "历史网页正文".getBytes(StandardCharsets.UTF_8));
        RawSnapshot snapshot = new RawSnapshot();
        snapshot.setId(9L);
        snapshot.setRequestUrl("https://example.com/article");
        snapshot.setContentType("text/html");
        snapshot.setCharsetName("UTF-8");
        snapshot.setBodyPath("raw/acquisition/2026-07-28/hash.html");
        RawSnapshotRepository repository = mock(RawSnapshotRepository.class);
        when(repository.findById(9L)).thenReturn(Optional.of(snapshot));

        FetchReplayService.ReplayPayload payload =
                new FetchReplayService(repository, root.toString()).replay(9L);

        assertEquals("历史网页正文", payload.getBodyText());
        assertEquals("https://example.com/article", payload.getRequestUrl());
        assertEquals("text/html", payload.getContentType());
    }
}
