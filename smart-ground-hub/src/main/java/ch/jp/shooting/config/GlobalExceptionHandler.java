package ch.jp.shooting.config;

import ch.jp.shooting.exception.*;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;

/**
 * Globaler Exception Handler für konsistente Error-Responses.
 * Behandelt alle Domain- und Infrastructure-Exceptions.
 */
@RestControllerAdvice
@NullMarked
public class GlobalExceptionHandler {

    // ── Device/Range Exceptions ──

    @ExceptionHandler(RangePositionNotFoundException.class)
    ProblemDetail handleRangePositionNotFound(RangePositionNotFoundException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("/errors/range-position-not-found"));
        return detail;
    }

    @ExceptionHandler(RangePositionOccupiedException.class)
    ProblemDetail handleRangePositionOccupied(RangePositionOccupiedException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("/errors/range-position-occupied"));
        return detail;
    }

    @ExceptionHandler(DeviceNotFoundException.class)
    ProblemDetail handleDeviceNotFound(DeviceNotFoundException ex) {
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

    // ── Session/Competition Exceptions ──

    @ExceptionHandler(SessionNotFoundException.class)
    ProblemDetail handleSessionNotFound(SessionNotFoundException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("/errors/session-not-found"));
        return detail;
    }

    @ExceptionHandler(InvalidGroupRegistrationException.class)
    ProblemDetail handleInvalidRegistration(InvalidGroupRegistrationException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setType(URI.create("/errors/invalid-registration"));
        return detail;
    }

    @ExceptionHandler(GroupAlreadyRegisteredException.class)
    ProblemDetail handleGroupAlreadyRegistered(GroupAlreadyRegisteredException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("/errors/group-already-registered"));
        return detail;
    }

    @ExceptionHandler(SessionStatusTransitionException.class)
    ProblemDetail handleStatusTransition(SessionStatusTransitionException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("/errors/invalid-status-transition"));
        return detail;
    }

    // ── Authentication Exceptions ──

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        detail.setType(URI.create("/errors/authentication-failed"));
        return detail;
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    ProblemDetail handleUsernameNotFound(UsernameNotFoundException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        detail.setType(URI.create("/errors/authentication-failed"));
        return detail;
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        detail.setType(URI.create("/errors/authentication-failed"));
        return detail;
    }

    // ── Play/Programme Exceptions ──

    @ExceptionHandler(BlockStateException.class)
    ProblemDetail handleBlockState(BlockStateException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("/errors/block-state-conflict"));
        return detail;
    }

    // ── Generic Exceptions ──

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail handleForbidden(ForbiddenException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setType(URI.create("/errors/forbidden"));
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

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneralException(Exception ex) {
        var detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ein interner Fehler ist aufgetreten"
        );
        detail.setType(URI.create("/errors/internal-error"));
        return detail;
    }
}
