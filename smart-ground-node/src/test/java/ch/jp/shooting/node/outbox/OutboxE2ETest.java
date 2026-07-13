package ch.jp.shooting.node.outbox;

import ch.jp.shooting.node.hub.HubClient;
import ch.jp.shooting.node.sync.NodeSerieReadService;
import ch.jp.smartground.model.PlayInstanceOutboxResult;
import ch.jp.smartground.model.SerieOutboxResult;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Beweist das Deliverable von Teilprojekt #3: Serie offline erstellen, sofort schiessen,
 * Hub anstecken — Serie und die darauf verweisende PlayInstance landen in kausaler
 * Reihenfolge; ein blockierter Vorgänger verhindert den Push seines Nachfolgers.
 */
@SpringBootTest
@Transactional
class OutboxE2ETest {

    @Autowired SerieOutboxService serieOutboxService;
    @Autowired PlayInstanceOutboxService playInstanceOutboxService;
    @Autowired OutboxDrainService drainService;
    @Autowired NodeSerieReadService readService;
    @MockitoBean HubClient hubClient;

    @Test
    void offlineSerieAndItsPlayInstance_areVisibleImmediately_thenDrainInCausalOrder() {
        var ownerId = UUID.randomUUID();

        UUID serieId = serieOutboxService.createSerieLocally("Offline-Serie", "user", null, ownerId, "[]");
        UUID instanceId = playInstanceOutboxService.startSerieInstanceLocally(serieId, "Offline-Serie", ownerId, "[]");

        // Read-your-writes: sofort sichtbar, bevor der Hub je etwas gesehen hat.
        var visibleBeforeDrain = readService.findAllVisible();
        assertThat(visibleBeforeDrain).filteredOn(v -> v.id().equals(serieId))
                .singleElement().matches(v -> v.provenance().equals("pending"));

        when(hubClient.pushSerieOutboxItem(any())).thenReturn(
                new SerieOutboxResult().status(SerieOutboxResult.StatusEnum.ACCEPTED)
                        .updatedAt(OffsetDateTime.now(ZoneOffset.UTC)));
        when(hubClient.pushPlayInstanceOutboxItem(any())).thenReturn(
                new PlayInstanceOutboxResult().status(PlayInstanceOutboxResult.StatusEnum.ACCEPTED));

        int sent = drainService.drainOnce();

        assertThat(sent).isEqualTo(2);
        InOrder order = inOrder(hubClient);
        order.verify(hubClient).pushSerieOutboxItem(argThat(item -> item.getId().equals(serieId)));
        order.verify(hubClient).pushPlayInstanceOutboxItem(argThat(item -> item.getInstanceId().equals(instanceId)));
    }

    @Test
    void serieConflict_neverPushesTheDependentPlayInstance() {
        var ownerId = UUID.randomUUID();

        UUID serieId = serieOutboxService.createSerieLocally("Offline-Serie", "user", null, ownerId, "[]");
        playInstanceOutboxService.startSerieInstanceLocally(serieId, "Offline-Serie", ownerId, "[]");

        when(hubClient.pushSerieOutboxItem(any())).thenReturn(
                new SerieOutboxResult().status(SerieOutboxResult.StatusEnum.CONFLICT)
                        .updatedAt(OffsetDateTime.now(ZoneOffset.UTC)).message("base_version mismatch"));

        int sent = drainService.drainOnce();

        assertThat(sent).isEqualTo(0);
        verify(hubClient).pushSerieOutboxItem(any());
        verify(hubClient, never()).pushPlayInstanceOutboxItem(any());
    }
}
