package ch.jp.shooting.config;

import ch.jp.shooting.exception.*;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;

/**
 * Globaler Exception Handler für konsistente Error-Responses.
 * Behandelt alle Domain- und Infrastructure-Exceptions.
 */
@RestControllerAdvice
@NullMarked
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("/errors/user-not-found"));
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

    @ExceptionHandler(TiebreakerNotFoundException.class)
    ProblemDetail handleTiebreakerNotFound(TiebreakerNotFoundException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("/errors/tiebreaker-not-found"));
        return detail;
    }

    @ExceptionHandler(InvalidTiebreakerStateException.class)
    ProblemDetail handleInvalidTiebreakerState(InvalidTiebreakerStateException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("/errors/invalid-tiebreaker-state"));
        return detail;
    }

    @ExceptionHandler(UnresolvedTiesException.class)
    org.springframework.http.ResponseEntity<ch.jp.smartground.model.UnresolvedTiesError> handleUnresolvedTies(
            UnresolvedTiesException ex) {
        var body = new ch.jp.smartground.model.UnresolvedTiesError()
                .message(ex.getMessage())
                .unresolvedTies(ex.getUnresolvedTies());
        return org.springframework.http.ResponseEntity.status(HttpStatus.CONFLICT).body(body);
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

    // ── OTA Exceptions ──

    @ExceptionHandler(OtaReleaseNotFoundException.class)
    ProblemDetail handleOtaReleaseNotFound(OtaReleaseNotFoundException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("/errors/ota-release-not-found"));
        return detail;
    }

    @ExceptionHandler(InvalidOtaArtifactException.class)
    ProblemDetail handleInvalidOtaArtifact(InvalidOtaArtifactException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setType(URI.create("/errors/invalid-ota-artifact"));
        return detail;
    }

    // ── Play/Passe Exceptions ──

    @ExceptionHandler(BlockStateException.class)
    ProblemDetail handleBlockState(BlockStateException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("/errors/block-state-conflict"));
        return detail;
    }

    // ── Routing Exceptions ──

    @ExceptionHandler(NoHandlerFoundException.class)
    ProblemDetail handleNoHandlerFound(NoHandlerFoundException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No endpoint found for " + ex.getHttpMethod() + " " + ex.getRequestURL());
        detail.setType(URI.create("/errors/endpoint-not-found"));
        return detail;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No endpoint found for " + ex.getHttpMethod() + " " + ex.getResourcePath());
        detail.setType(URI.create("/errors/endpoint-not-found"));
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        detail.setType(URI.create("/errors/validation-failed"));
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setType(URI.create("/errors/bad-request"));
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneralException(Exception ex) {
        log.error("Unbehandelter Fehler", ex);
        var detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ein interner Fehler ist aufgetreten"
        );
        detail.setType(URI.create("/errors/internal-error"));
        return detail;
    }
}
