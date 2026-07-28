package com.finscope.service.fetch;

import com.finscope.dao.fetch.RawSnapshotRepository;
import com.finscope.domain.fetch.RawSnapshot;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RawSnapshotStoreTest {

    @Test
    void savesContentAddressedBodyAndRedactedRequestMetadata(@TempDir Path root) throws Exception {
        RawSnapshotRepository repository = mock(RawSnapshotRepository.class);
        when(repository.save(any(RawSnapshot.class))).thenAnswer(invocation -> {
            RawSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(7L);
            return snapshot;
        });
        RawSnapshotStore store = new RawSnapshotStore(repository, root.toString());
        AcquisitionRequest request = AcquisitionRequest.get(URI.create("https://example.com/article"))
                .purpose("WEB_ARTICLE")
                .header("Accept", "text/html")
                .header("Cookie", "session=secret")
                .build();
        byte[] body = "可离线重放的正文".getBytes(StandardCharsets.UTF_8);
        AcquisitionResponse response = new AcquisitionResponse(
                request.getUri(), request.getUri(), 200, Collections.emptyMap(), body,
                "可离线重放的正文", "text/html; charset=utf-8", "UTF-8",
                "a76b7b14f2b6f823c857b36d0598365ff2d02fd2dc9f20ad3af777a0830e1c98",
                1, 12, Instant.parse("2026-07-28T06:00:00Z"));

        RawSnapshot snapshot = store.save(request, response, 11L, 22L);

        assertEquals(7L, snapshot.getId());
        assertTrue(snapshot.getRequestHeadersJson().contains("Accept"));
        assertFalse(snapshot.getRequestHeadersJson().contains("secret"));
        Path bodyFile = root.resolve(snapshot.getBodyPath());
        assertTrue(Files.exists(bodyFile));
        assertEquals("可离线重放的正文", new String(Files.readAllBytes(bodyFile), StandardCharsets.UTF_8));
    }
}
