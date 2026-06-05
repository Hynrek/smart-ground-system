package ch.jp.shooting.service;

import ch.jp.shooting.model.SessionStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionServiceStatusTest {

    @Test
    void sessionStatus_hasOpenAndPreComplete() {
        assertNotNull(SessionStatus.valueOf("OPEN"));
        assertNotNull(SessionStatus.valueOf("PRE_COMPLETE"));
    }

    @Test
    void sessionStatus_hasSixValues() {
        assertEquals(6, SessionStatus.values().length);
    }
}
