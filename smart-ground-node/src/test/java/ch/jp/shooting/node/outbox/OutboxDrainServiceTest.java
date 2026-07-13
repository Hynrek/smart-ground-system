package ch.jp.shooting.node.outbox;

import ch.jp.shooting.node.hub.HubClient;
import ch.jp.smartground.model.PlayInstanceOutboxResult;
import ch.jp.smartground.model.SerieOutboxItem;
import ch.jp.smartground.model.SerieOutboxResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class OutboxDrainServiceTest {

    @Autowired OutboxDrainService drainService;
    @Autowired OutboxEntryRepository outboxRepository;
    @Autowired @Qualifier("outboxObjectMapper") ObjectMapper objectMapper;
    @MockitoBean HubClient hubClient;

    private OutboxEntry serieEntry(String status) {
        var e = new OutboxEntry();
        e.setEntityType("SERIE");
        var id = UUID.randomUUID();
        e.setEntityId(id);
        var item = new SerieOutboxItem().id(id).name("x").ownership("user")
                .ownerId(UUID.randomUUID()).stepsJson("[]").published(false);
        e.setPayloadJson(writeJson(item));
        e.setStatus(status);
        e.setCreatedAt(Instant.now());
        e.setAttempts(0);
        return outboxRepository.save(e);
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    @Test
    void drainOnce_accepted_marksSent_andReturnsCountOfOne() {
        serieEntry("PENDING");
        when(hubClient.pushSerieOutboxItem(any())).thenReturn(
                new SerieOutboxResult().status(SerieOutboxResult.StatusEnum.ACCEPTED)
                        .updatedAt(OffsetDateTime.now(ZoneOffset.UTC)));

        int sent = drainService.drainOnce();

        assertThat(sent).isEqualTo(1);
        assertThat(outboxRepository.findByStatusOrderBySequenceAsc("PENDING")).isEmpty();
        assertThat(outboxRepository.findAll().get(0).getStatus()).isEqualTo("SENT");
    }

    @Test
    void drainOnce_conflict_marksFailed_stopsBeforeLaterEntries() {
        serieEntry("PENDING");
        serieEntry("PENDING"); // must never be attempted — FIFO/single-flight
        when(hubClient.pushSerieOutboxItem(any())).thenReturn(
                new SerieOutboxResult().status(SerieOutboxResult.StatusEnum.CONFLICT)
                        .updatedAt(OffsetDateTime.now(ZoneOffset.UTC)).message("base_version mismatch"));

        int sent = drainService.drainOnce();

        assertThat(sent).isEqualTo(0);
        verify(hubClient, times(1)).pushSerieOutboxItem(any());
        var rows = outboxRepository.findAll();
        assertThat(rows).filteredOn(r -> "FAILED".equals(r.getStatus())).hasSize(1);
        assertThat(rows).filteredOn(r -> "PENDING".equals(r.getStatus())).hasSize(1);
    }

    @Test
    void drainOnce_transportError_leavesEntryPending_stopsQueue() {
        serieEntry("PENDING");
        serieEntry("PENDING");
        when(hubClient.pushSerieOutboxItem(any())).thenThrow(new RuntimeException("connection refused"));

        int sent = drainService.drainOnce();

        assertThat(sent).isEqualTo(0);
        verify(hubClient, times(1)).pushSerieOutboxItem(any());
        assertThat(outboxRepository.findByStatusOrderBySequenceAsc("PENDING")).hasSize(2);
        assertThat(outboxRepository.findAll().get(0).getAttempts()).isEqualTo(1);
    }
}
