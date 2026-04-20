package com.trivyexplorer.web;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> illegalState(IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Bad request"));
  }

  @ExceptionHandler(IOException.class)
  public ResponseEntity<Map<String, String>> io(IOException e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Scan failed"));
  }

  @ExceptionHandler(InterruptedException.class)
  public ResponseEntity<Map<String, String>> interrupted(InterruptedException e) {
    Thread.currentThread().interrupt();
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Interrupted"));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) {
    String msg =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .orElse("Validation failed");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", msg));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
    HttpStatus status =
        e.getStatusCode() instanceof HttpStatus h ? h : HttpStatus.BAD_REQUEST;
    String message =
        e.getReason() != null && !e.getReason().isBlank() ? e.getReason() : status.getReasonPhrase();
    return ResponseEntity.status(status).body(Map.of("message", message));
  }
}
