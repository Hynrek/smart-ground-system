package ch.jp.shooting.service;

import ch.jp.shooting.model.SessionStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionServiceStatusTest {

    @Test
    void sessionStatus_hasPreCompleteButNoOpen() {
        assertNotNull(SessionStatus.valueOf("PRE_COMPLETE"));
        assertThrows(IllegalArgumentException.class, () -> SessionStatus.valueOf("OPEN"));
    }

    @Test
    void sessionStatus_hasFiveValues() {
        assertEquals(5, SessionStatus.values().length);
    }
}
