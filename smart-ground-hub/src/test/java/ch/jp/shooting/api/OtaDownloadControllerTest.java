package ch.jp.shooting.api;

import ch.jp.shooting.service.OtaArtifactStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OtaDownloadControllerTest {

    OtaArtifactStore store;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        store = mock(OtaArtifactStore.class);
        mvc = MockMvcBuilders.standaloneSetup(new OtaDownloadController(store)).build();
    }

    @Test
    void servesManifest() throws Exception {
        when(store.readAppFile("0.7", "manifest.json"))
            .thenReturn("{\"appVersion\":\"0.7\"}".getBytes());
        mvc.perform(get("/api/ota/app/0.7/manifest.json"))
            .andExpect(status().isOk())
            .andExpect(content().string("{\"appVersion\":\"0.7\"}"));
    }

    @Test
    void servesNestedFile() throws Exception {
        when(store.readAppFile("0.7", "files/boards/xiao_esp32s3.py"))
            .thenReturn("BOX".getBytes());
        mvc.perform(get("/api/ota/app/0.7/files/boards/xiao_esp32s3.py"))
            .andExpect(status().isOk())
            .andExpect(content().bytes("BOX".getBytes()));
        verify(store).readAppFile("0.7", "files/boards/xiao_esp32s3.py");
    }

    @Test
    void servesFirmwareBin() throws Exception {
        when(store.readFirmwareImage("mp-1.24")).thenReturn(new byte[]{1, 2, 3});
        mvc.perform(get("/api/ota/firmware/mp-1.24"))
            .andExpect(status().isOk())
            .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }
}
