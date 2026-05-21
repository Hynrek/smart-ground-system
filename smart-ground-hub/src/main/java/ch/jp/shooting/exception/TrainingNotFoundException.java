package ch.jp.shooting.exception;

import java.util.UUID;

public class TrainingNotFoundException extends NotFoundException {
    public TrainingNotFoundException(UUID id) {
        super("Training nicht gefunden: " + id);
    }
}
