package ch.jp.shooting.exception;

import ch.jp.smartground.model.TiedBlock;
import java.util.List;

public class UnresolvedTiesException extends RuntimeException {
    private final transient List<TiedBlock> unresolvedTies;

    public UnresolvedTiesException(List<TiedBlock> unresolvedTies) {
        super("Unresolved decisive ties; finish requires force=true");
        this.unresolvedTies = unresolvedTies;
    }

    public List<TiedBlock> getUnresolvedTies() { return unresolvedTies; }
}
