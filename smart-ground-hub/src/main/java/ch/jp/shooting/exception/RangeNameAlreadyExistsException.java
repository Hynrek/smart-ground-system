package ch.jp.shooting.exception;

public class RangeNameAlreadyExistsException extends RuntimeException {

    public RangeNameAlreadyExistsException(String name) {
        super("Range with name '" + name + "' already exists");
    }
}