package ch.jp.shooting.node.sync;

import ch.jp.shooting.node.outbox.OutboxEntry;
import ch.jp.shooting.node.outbox.OutboxEntryRepository;
import ch.jp.smartground.model.SerieOutboxItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class NodeSerieReadServiceTest {

    @Autowired NodeSerieReadService readService;
    @Autowired SyncedSerieRepository syncedSerieRepository;
    @Autowired OutboxEntryRepository outboxRepository;
    @Autowired @Qualifier("outboxObjectMapper") ObjectMapper objectMapper;

    private SyncedSerie synced(UUID id, String name, boolean deleted) {
        var s = new SyncedSerie();
        s.setId(id);
        s.setName(name);
        s.setOwnership("user");
        s.setOwnerId(UUID.randomUUID());
        s.setStepsJson("[]");
        s.setPublished(false);
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        s.setDeleted(deleted);
        return syncedSerieRepository.save(s);
    }

    private void outboxEntry(UUID id, String name, String status) throws Exception {
        var item = new SerieOutboxItem().id(id).name(name).ownership("user")
                .ownerId(UUID.randomUUID()).stepsJson("[]").published(false);
        var e = new OutboxEntry();
        e.setEntityType("SERIE");
        e.setEntityId(id);
        e.setPayloadJson(objectMapper.writeValueAsString(item));
        e.setStatus(status);
        e.setCreatedAt(Instant.now());
        e.setAttempts(0);
        outboxRepository.save(e);
    }

    @Test
    void findAllVisible_unionsSyncedAndPendingOutbox_hidesDeletedAndSent() throws Exception {
        var syncedId = UUID.randomUUID();
        var deletedId = UUID.randomUUID();
        var pendingId = UUID.randomUUID();
        var failedId = UUID.randomUUID();
        var sentId = UUID.randomUUID();

        synced(syncedId, "Aus dem Hub", false);
        synced(deletedId, "Gelöscht", true);
        outboxEntry(pendingId, "Offline neu", "PENDING");
        outboxEntry(failedId, "Offline abgelehnt", "FAILED");
        outboxEntry(sentId, "Schon bestätigt", "SENT");

        var visible = readService.findAllVisible();

        assertThat(visible).extracting(VisibleSerie::id)
                .containsExactlyInAnyOrder(syncedId, pendingId, failedId);
        assertThat(visible).filteredOn(v -> v.id().equals(syncedId))
                .singleElement().matches(v -> v.provenance().equals("synced"));
        assertThat(visible).filteredOn(v -> v.id().equals(pendingId))
                .singleElement().matches(v -> v.provenance().equals("pending"));
        assertThat(visible).filteredOn(v -> v.id().equals(failedId))
                .singleElement().matches(v -> v.provenance().equals("failed"));
    }

    @Test
    void findAllVisible_whenSameIdInSyncedAndPending_prefersOutboxEntry() throws Exception {
        UUID sharedId = UUID.randomUUID();
        synced(sharedId, "Original from Hub", false);
        outboxEntry(sharedId, "Edited Offline", "PENDING");

        var visible = readService.findAllVisible();

        assertThat(visible).filteredOn(v -> v.id().equals(sharedId))
                .singleElement().matches(v -> v.provenance().equals("pending")
                        && v.name().equals("Edited Offline"));
    }
}
