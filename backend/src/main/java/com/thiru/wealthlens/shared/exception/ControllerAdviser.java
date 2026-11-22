package com.thiru.wealthlens.shared.exception;

import com.thiru.wealthlens.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

@Log4j2
@RestControllerAdvice
public class ControllerAdviser {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception ex, HttpServletRequest request) {
        log(ex);
        return problem(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), request);
    }

    /**
     * A body the server cannot read is the caller's to correct; the answer names the field and what it accepts, never the
     * parser's own text, which names library settings and controller signatures (W-305).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log(ex);
        return problem(HttpStatus.BAD_REQUEST.value(), unreadable(ex), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log(ex);
        String message = "Required request parameter '" + ex.getParameterName() + "' is not present";
        return problem(HttpStatus.BAD_REQUEST.value(), message, request);
    }

    /** A value that is not of its parameter's type is the caller's to correct, so the answer names what is accepted (W-263). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log(ex);
        Class<?> type = ex.getParameter().nestedIfOptional().getNestedParameterType();
        String message = "Parameter '" + ex.getName() + "' must be " + accepted(type) + ", but was '" + ex.getValue() + "'";
        return problem(HttpStatus.BAD_REQUEST.value(), message, request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ProblemDetail> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        log(ex);
        String message = "Required part '" + ex.getRequestPartName() + "' is not present";
        return problem(HttpStatus.BAD_REQUEST.value(), message, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(Exception ex, HttpServletRequest request) {
        log(ex);
        return problem(HttpStatus.METHOD_NOT_ALLOWED.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(Exception ex, HttpServletRequest request) {
        log(ex);
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), ex.getMessage(), request);
    }

    /** A file past {@code spring.servlet.multipart.max-file-size}: the caller's to fix, so 400, not a 500 (W-131d1). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log(ex);
        return problem(HttpStatus.BAD_REQUEST.value(), "The file is larger than the " + ex.getMaxUploadSize()
                + " bytes an upload may be", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(Exception ex, HttpServletRequest request) {
        log(ex);
        return problem(HttpStatus.UNAUTHORIZED.value(), ex.getMessage(), request);
    }

    /** A body missing what its type requires (W-358d4): 400 naming each field, in field order. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log(ex);
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> "field '" + error.getField() + "' " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST.value(), Character.toUpperCase(fields.charAt(0)) + fields.substring(1), request);
    }

    /** Over a limit on attempts (W-358d2): 429, with how long to wait. */
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyAttempts(TooManyAttemptsException ex, HttpServletRequest request) {
        log(ex);
        ResponseEntity<ProblemDetail> answer = problem(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage(), request);
        return ResponseEntity.status(answer.getStatusCode()).header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.getRetryAfter().toSeconds())))
                .body(answer.getBody());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(Exception ex, HttpServletRequest request) {
        log(ex);
        return problem(HttpStatus.FORBIDDEN.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleServiceUnavailable(Exception ex, HttpServletRequest request) {
        log(ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(TradeCollisionException.class)
    public ResponseEntity<ProblemDetail> handleConflict(Exception ex, HttpServletRequest request) {
        log(ex);
        return problem(HttpStatus.CONFLICT.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(Exception ex, HttpServletRequest request) {
        log(ex);
        return problem(HttpStatus.NOT_FOUND.value(), ex.getMessage(), request);
    }

    /**
     * An unexpected failure (W-364): its message names the code's insides — classes, nulls — so it goes only to the log, and the caller
     * is told it failed and given the request's id to quote (W-358d1).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        log(ex);
        String detail = RequestIdFilter.current().map(id -> "Something went wrong. Quote " + RequestIdFilter.HEADER + " " + id + " when you report it.")
                .orElse("Something went wrong.");
        return problem(HttpStatus.INTERNAL_SERVER_ERROR.value(), detail, request);
    }

    /** Every error as RFC 9457 {@code ProblemDetail} (W-359a): the status, its reason as the title, the message, the path. */
    private static ResponseEntity<ProblemDetail> problem(int status, String message, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), message);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(problem);
    }

    private static String unreadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() == null) {
            return "The request body is missing";
        }
        Throwable cause = ex.getCause();
        if (cause instanceof UnrecognizedPropertyException unknown) {
            return "Field '" + path(unknown) + "' is not one this request accepts";
        }
        if (cause instanceof InvalidFormatException format) {
            return "Field '" + path(format) + "' must be " + accepted(format.getTargetType()) + ", but was '" + format.getValue() + "'";
        }
        if (cause instanceof MismatchedInputException mismatch && mismatch.getTargetType() != null) {
            return mismatch.getPath().isEmpty()
                    ? "The request body must be a JSON " + (isList(mismatch.getTargetType()) ? "array" : "object")
                    : "Field '" + path(mismatch) + "' must be " + accepted(mismatch.getTargetType());
        }
        if (cause instanceof StreamReadException malformed && malformed.getLocation() != null) {
            return "The request body is not valid JSON (line " + malformed.getLocation().getLineNr() + ", column "
                    + malformed.getLocation().getColumnNr() + ")";
        }
        return "The request body could not be read";
    }

    private static boolean isList(Class<?> type) {
        return type.isArray() || java.util.Collection.class.isAssignableFrom(type);
    }

    /** The field as the caller wrote it: {@code rules[0].perLot}. */
    private static String path(JacksonException ex) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : ex.getPath()) {
            if (reference.getPropertyName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(reference.getPropertyName());
            } else {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    /** What a parameter of {@code type} accepts, in the caller's words; the formats are the ones the controllers bind. */
    private static String accepted(Class<?> type) {
        if (type.isEnum()) {
            return "one of " + Arrays.toString(type.getEnumConstants());
        }
        if (type == double.class || type == Double.class) {
            return "a number";
        }
        if (type == int.class) {
            return "a whole number";
        }
        if (type == boolean.class) {
            return "true or false";
        }
        if (type == LocalDate.class) {
            return "a date as yyyy-MM-dd";
        }
        if (type == LocalDateTime.class) {
            return "a date and time as yyyy-MM-dd'T'HH:mm:ss";
        }
        return "a " + type.getSimpleName();
    }

    private static void log(Exception ex) {
        log.error(ex.getMessage(), ex);
    }
}
