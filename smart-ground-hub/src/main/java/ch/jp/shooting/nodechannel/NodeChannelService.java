package ch.jp.shooting.nodechannel;

import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Dispatch + Ack-Korrelation des node-channel. Task 3 füllt dispatchCommand; hier nur die Ack-Seam. */
@Service
@NullMarked
public class NodeChannelService {

    /** Vom Handler bei COMMAND_ACK aufgerufen — schliesst das wartende Future (Task 3). */
    public void onCommandAck(UUID commandId, String outcome) {
        // Task 3: pending future completen
    }
}
