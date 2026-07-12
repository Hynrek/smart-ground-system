package ch.jp.shooting.node.sync;

import ch.jp.shooting.node.hub.HubClient;
import ch.jp.smartground.model.SerieSyncItem;
import ch.jp.smartground.model.SerieSyncPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class SerieSyncServiceTest {

    @Autowired SyncedSerieRepository syncedSerieRepository;
    @Autowired SyncStateRepository syncStateRepository;

    private SerieSyncItem item(UUID id, String name, boolean deleted, Instant updatedAt) {
        return new SerieSyncItem()
                .id(id).name(name).ownership("user").ownerId(UUID.randomUUID())
                .stepsJson("[]").published(false)
                .createdAt(OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
                .deleted(deleted);
    }

    private SerieSyncPage page(List<SerieSyncItem> items, Instant nextCursor, boolean hasMore) {
        return new SerieSyncPage()
                .items(items)
                .nextCursor(OffsetDateTime.ofInstant(nextCursor, ZoneOffset.UTC))
                .hasMore(hasMore);
    }

    @Test
    void sync_drainsAllPages_upsertsItemsAndTombstones_persistsCursor() {
        var id1 = UUID.randomUUID();
        var id2 = UUID.randomUUID();
        var t1 = Instant.parse("2026-07-12T10:00:00Z");
        var t2 = Instant.parse("2026-07-12T11:00:00Z");
        var hub = mock(HubClient.class);
        // page 1 full (hasMore) → page 2 partial (done)
        when(hub.fetchSerieSyncPage(any(Instant.class), anyInt()))
            .thenReturn(page(List.of(item(id1, "alive", false, t1)), t1, true))
            .thenReturn(page(List.of(item(id2, "dead", true, t2)), t2, false));

        var service = new SerieSyncService(hub, syncedSerieRepository, syncStateRepository);
        int applied = service.sync();

        assertThat(applied).isEqualTo(2);
        assertThat(syncedSerieRepository.findById(id1)).get()
            .matches(s -> !s.isDeleted() && s.getName().equals("alive"));
        assertThat(syncedSerieRepository.findById(id2)).get()
            .matches(SyncedSerie::isDeleted);
        // cursor persisted at the last page's nextCursor
        assertThat(syncStateRepository.findById("serie")).get()
            .matches(st -> st.getCursor().equals(t2));
    }

    @Test
    void sync_secondRun_resumesFromPersistedCursor() {
        var seeded = Instant.parse("2026-07-12T09:00:00Z");
        var st = new SyncState();
        st.setEntity("serie");
        st.setCursor(seeded);
        syncStateRepository.save(st);

        var hub = mock(HubClient.class);
        when(hub.fetchSerieSyncPage(any(Instant.class), anyInt()))
            .thenReturn(page(List.of(), seeded, false));

        var service = new SerieSyncService(hub, syncedSerieRepository, syncStateRepository);
        service.sync();

        // it asked the hub starting from the persisted cursor, not EPOCH
        var captor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(hub).fetchSerieSyncPage(captor.capture(), anyInt());
        assertThat(captor.getValue()).isEqualTo(seeded);
    }
}
