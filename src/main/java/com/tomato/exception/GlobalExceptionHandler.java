package com.tomato.exception;

import com.tomato.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        String message = ex.getMessage();

        HttpStatus status = switch (errorCode) {
            case ERROR_401_2200, ERROR_401_2201 -> HttpStatus.UNAUTHORIZED;
            case ERROR_403_2302, ERROR_403_2309 -> HttpStatus.FORBIDDEN;
            case ERROR_404_2001, ERROR_404_2300 -> HttpStatus.NOT_FOUND;
            case ERROR_409_2002, ERROR_409_2003, ERROR_409_2301, ERROR_409_2306, ERROR_409_2308 -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };

        return new ResponseEntity<>(ApiResponse.error(errorCode, message), status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorDetails = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return new ResponseEntity<>(
                ApiResponse.error(ErrorCode.ERROR_400_VALIDATION, errorDetails),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        return new ResponseEntity<>(
                ApiResponse.error(ErrorCode.ERROR_500_5000, "An unexpected internal error occurred."),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
