package ch.jp.shooting.exception;

import java.util.UUID;

public class ProgrammNotFoundException extends NotFoundException {
    public ProgrammNotFoundException(UUID id) {
        super("Programm nicht gefunden: " + id);
    }
}
