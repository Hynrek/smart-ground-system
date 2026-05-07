package ch.jp.shooting.api;

import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.exception.DeviceAlreadyAssignedException;
import ch.jp.shooting.exception.DeviceNotFoundException;
import ch.jp.shooting.exception.DeviceTemplateNotFoundException;
import ch.jp.shooting.exception.NotFoundException;
import ch.jp.shooting.exception.RangeHasDevicesException;
import ch.jp.shooting.exception.RangeNameAlreadyExistsException;
import ch.jp.shooting.exception.RangeNotFoundException;
import ch.jp.shooting.exception.SmartBoxNotFoundException;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
@NullMarked
public class GlobalExceptionHandler {

    @ExceptionHandler(DeviceNotFoundException.class)
    ProblemDetail handleNotFound(DeviceNotFoundException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("/errors/device-not-found"));
        return detail;
    }

    @ExceptionHandler(DeviceTemplateNotFoundException.class)
    ProblemDetail handleTemplateNotFound(DeviceTemplateNotFoundException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("/errors/device-type-not-found"));
        return detail;
    }

    @ExceptionHandler(RangeNotFoundException.class)
    ProblemDetail handleRangeNotFound(RangeNotFoundException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("/errors/range-not-found"));
        return detail;
    }

    @ExceptionHandler(SmartBoxNotFoundException.class)
    ProblemDetail handleSmartBoxNotFound(SmartBoxNotFoundException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("/errors/smartbox-not-found"));
        return detail;
    }

    @ExceptionHandler(RangeNameAlreadyExistsException.class)
    ProblemDetail handleRangeNameExists(RangeNameAlreadyExistsException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("/errors/range-name-exists"));
        return detail;
    }

    @ExceptionHandler(DeviceAlreadyAssignedException.class)
    ProblemDetail handleDeviceAssigned(DeviceAlreadyAssignedException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("/errors/device-already-assigned"));
        return detail;
    }

    @ExceptionHandler(RangeHasDevicesException.class)
    ProblemDetail handleRangeHasDevices(RangeHasDevicesException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("/errors/range-has-devices"));
        return detail;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("/errors/not-found"));
        return detail;
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("/errors/conflict"));
        return detail;
    }
}