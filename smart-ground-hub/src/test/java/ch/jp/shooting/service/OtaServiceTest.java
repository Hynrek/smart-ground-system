package ch.jp.shooting.service;

import ch.jp.shooting.config.OtaPublishService;
import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import ch.jp.shooting.repository.OtaReleaseRepository;
import ch.jp.shooting.repository.SmartBoxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtaServiceTest {

    @Mock OtaArtifactStore store;
    @Mock OtaReleaseRepository repository;
    @Mock SmartBoxRepository smartBoxRepository;
    @Mock OtaPublishService publishService;
    @InjectMocks OtaService service;

    @Test
    void uploadAppStoresArtifactAndRegistersRelease() {
        when(store.storeAppBundle("0.7", new byte[]{1}))
            .thenReturn(new OtaArtifactStore.StoredApp("deadbeef", 42L));
        when(repository.save(any(OtaRelease.class))).thenAnswer(i -> i.getArgument(0));

        OtaRelease saved = service.uploadApp("0.7", new byte[]{1});

        assertThat(saved.getType()).isEqualTo(OtaType.APP);
        assertThat(saved.getVersion()).isEqualTo("0.7");
        assertThat(saved.getSha256()).isEqualTo("deadbeef");
        assertThat(saved.getSizeBytes()).isEqualTo(42L);

        ArgumentCaptor<OtaRelease> cap = ArgumentCaptor.forClass(OtaRelease.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(OtaType.APP);
    }

    @Test
    void uploadFirmwareStoresImageAndRegistersRelease() {
        when(store.storeFirmwareImage("mp-1.24", new byte[]{9}))
            .thenReturn(new OtaArtifactStore.StoredFirmware("cafe", 5L));
        when(repository.save(any(OtaRelease.class))).thenAnswer(i -> i.getArgument(0));

        OtaRelease saved = service.uploadFirmware("mp-1.24", new byte[]{9});

        assertThat(saved.getType()).isEqualTo(OtaType.FIRMWARE);
        assertThat(saved.getSha256()).isEqualTo("cafe");
    }
}
