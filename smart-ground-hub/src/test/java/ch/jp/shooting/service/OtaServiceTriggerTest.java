package ch.jp.shooting.service;

import ch.jp.shooting.config.OtaPublishService;
import ch.jp.shooting.exception.OtaReleaseNotFoundException;
import ch.jp.shooting.exception.SmartBoxNotFoundException;
import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.OtaReleaseRepository;
import ch.jp.shooting.repository.SmartBoxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtaServiceTriggerTest {

    @Mock OtaArtifactStore store;
    @Mock OtaReleaseRepository releaseRepository;
    @Mock SmartBoxRepository smartBoxRepository;
    @Mock OtaPublishService publishService;

    OtaService service() {
        return new OtaService(store, releaseRepository, smartBoxRepository, publishService);
    }

    @Test
    void triggerPublishesForResolvedBoxAndRelease() {
        UUID boxId = UUID.randomUUID();
        SmartBox box = new SmartBox();
        box.setMacAddress("aabbccddeeff");
        OtaRelease release = new OtaRelease();
        release.setType(OtaType.APP);
        release.setVersion("0.7");
        when(smartBoxRepository.findById(boxId)).thenReturn(Optional.of(box));
        when(releaseRepository.findByTypeAndVersion(OtaType.APP, "0.7")).thenReturn(Optional.of(release));

        service().triggerOta(boxId, OtaType.APP, "0.7");

        verify(publishService).publish(box, release);
    }

    @Test
    void triggerThrowsWhenBoxMissing() {
        UUID boxId = UUID.randomUUID();
        when(smartBoxRepository.findById(boxId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().triggerOta(boxId, OtaType.APP, "0.7"))
            .isInstanceOf(SmartBoxNotFoundException.class);
    }

    @Test
    void triggerThrowsWhenReleaseMissing() {
        UUID boxId = UUID.randomUUID();
        when(smartBoxRepository.findById(boxId)).thenReturn(Optional.of(new SmartBox()));
        when(releaseRepository.findByTypeAndVersion(OtaType.APP, "9.9")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().triggerOta(boxId, OtaType.APP, "9.9"))
            .isInstanceOf(OtaReleaseNotFoundException.class);
    }
}
