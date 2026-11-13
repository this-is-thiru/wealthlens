package com.thiru.wealthlens.shared.exception;


import com.thiru.wealthlens.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        log(ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), request);
    }

    /**
     * A body the server cannot read is the caller's to correct; the answer names the field and what it accepts, never the
     * parser's own text, which names library settings and controller signatures (W-305).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log(ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), unreadable(ex), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log(ex);
        String message = "Required request parameter '" + ex.getParameterName() + "' is not present";
        return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), message, request);
    }

    /** A value that is not of its parameter's type is the caller's to correct, so the answer names what is accepted (W-263). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log(ex);
        Class<?> type = ex.getParameter().nestedIfOptional().getNestedParameterType();
        String message = "Parameter '" + ex.getName() + "' must be " + accepted(type) + ", but was '" + ex.getValue() + "'";
        return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), message, request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        log(ex);
        String message = "Required part '" + ex.getRequestPartName() + "' is not present";
        return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), message, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(Exception ex, HttpServletRequest request) {
        log(ex);
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(Exception ex, HttpServletRequest request) {
        log(ex);
        return buildErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(Exception ex, HttpServletRequest request) {
        log(ex);
        return buildErrorResponse(HttpStatus.UNAUTHORIZED.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(Exception ex, HttpServletRequest request) {
        log(ex);
        return buildErrorResponse(HttpStatus.FORBIDDEN.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(Exception ex, HttpServletRequest request) {
        log(ex);
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(TradeCollisionException.class)
    public ResponseEntity<ErrorResponse> handleConflict(Exception ex, HttpServletRequest request) {
        log(ex);
        return buildErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
        log(ex);
        return buildErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log(ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage(), request);
    }

    private static ResponseEntity<ErrorResponse> buildErrorResponse(int status, String message, HttpServletRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setTimestamp(Instant.now().toString());
        errorResponse.setStatus(status);
        errorResponse.setError(HttpStatus.valueOf(status).getReasonPhrase());
        errorResponse.setMessage(message);
        errorResponse.setPath(request.getRequestURI());
        return ResponseEntity.status(status).body(errorResponse);
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
