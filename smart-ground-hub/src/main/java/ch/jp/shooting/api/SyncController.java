package ch.jp.shooting.api;

import ch.jp.shooting.service.SerieSyncService;
import ch.jp.smartground.api.SyncApi;
import ch.jp.smartground.model.SerieSyncPage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@NullMarked
public class SyncController implements SyncApi {

    private final SerieSyncService serieSyncService;

    public SyncController(SerieSyncService serieSyncService) {
        this.serieSyncService = serieSyncService;
    }

    @Override
    public ResponseEntity<SerieSyncPage> syncSerien(@Nullable OffsetDateTime updatedAfter,
                                                    @Nullable Integer limit) {
        return ResponseEntity.ok(serieSyncService.syncSerien(updatedAfter, limit));
    }
}
