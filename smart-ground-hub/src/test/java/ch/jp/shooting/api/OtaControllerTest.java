package ch.jp.shooting.api;

import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.service.OtaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtaControllerTest {

    @Mock OtaService otaService;

    @Test
    void uploadAppReturns201() throws Exception {
        OtaRelease release = new OtaRelease();
        release.setType(OtaType.APP);
        release.setVersion("0.7");
        when(otaService.uploadApp(eq("0.7"), any())).thenReturn(release);

        var controller = new OtaController(otaService);
        var file = new MockMultipartFile("file", "bundle.zip", "application/zip", new byte[]{1});
        var resp = controller.uploadOtaRelease(
            ch.jp.smartground.model.OtaTypeValue.APP, "0.7", file);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getVersion()).isEqualTo("0.7");
        verify(otaService).uploadApp("0.7", new byte[]{1});
    }

    @Test
    void triggerReturns202() {
        UUID id = UUID.randomUUID();
        var controller = new OtaController(otaService);
        var req = new ch.jp.smartground.model.TriggerOtaRequest()
            .type(ch.jp.smartground.model.OtaTypeValue.APP).version("0.7");
        var resp = controller.triggerSmartBoxOta(id, req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(otaService).triggerOta(id, OtaType.APP, "0.7");
    }

    @Test
    void statusReflectsBoxFields() {
        UUID id = UUID.randomUUID();
        SmartBox box = new SmartBox();
        box.setOtaPhase("APPLIED");
        box.setOtaVersion("0.7");
        box.setOtaProgress(100);
        when(otaService.getBox(id)).thenReturn(box);

        var controller = new OtaController(otaService);
        var resp = controller.getSmartBoxOtaStatus(id);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getPhase()).isEqualTo("APPLIED");
        assertThat(resp.getBody().getProgress()).isEqualTo(100);
    }
}
