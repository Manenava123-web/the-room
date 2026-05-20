package com.theroom.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleAppException(AppException ex) {
        log.warn("[AppException] {} — status {}", ex.getMessage(), ex.getStatus().value());
        return ResponseEntity.status(ex.getStatus()).body(errorBody(ex.getMessage(), ex.getStatus()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        Map<String, Object> body = errorBody("Error de validación", HttpStatus.BAD_REQUEST);
        body.put("errores", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> handleMultipart(MultipartException ex) {
        String msg = ex instanceof MaxUploadSizeExceededException
                ? "El archivo supera el tamaño máximo permitido (5 MB)"
                : "Error al procesar el archivo. La subida fue interrumpida.";
        log.warn("[MultipartException] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(msg, HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody("Recurso no encontrado", HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("[500] Excepción no controlada: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("Error interno del servidor", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private Map<String, Object> errorBody(String message, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("message", message);
        return body;
    }
}
