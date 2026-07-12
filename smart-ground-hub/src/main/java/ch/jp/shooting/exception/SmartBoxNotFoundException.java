package ch.jp.shooting.exception;

import org.jspecify.annotations.NullMarked;
import java.util.UUID;

import static org.yaml.snakeyaml.nodes.Tag.STR;

@NullMarked
public class SmartBoxNotFoundException extends RuntimeException {
    public SmartBoxNotFoundException(UUID id) {
        super("SmartBox nicht gefunden: "+id);
    }
}