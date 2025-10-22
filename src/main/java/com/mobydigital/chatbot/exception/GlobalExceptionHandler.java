package com.mobydigital.chatbot.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Manejar excepciones de documento no encontrado
     */
    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentNotFound(DocumentNotFoundException ex) {
        logger.warn("Document not found: {}", ex.getMessage());

        Map<String, Object> response = createErrorResponse(
            ex.getMessage(),
            ex.getErrorCode(),
            HttpStatus.NOT_FOUND
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Manejar excepciones generales del chatbot
     */
    @ExceptionHandler(ChatbotException.class)
    public ResponseEntity<Map<String, Object>> handleChatbotException(ChatbotException ex) {
        logger.error("Chatbot exception: {}", ex.getMessage(), ex);

        Map<String, Object> response = createErrorResponse(
            ex.getMessage(),
            ex.getErrorCode(),
            HttpStatus.INTERNAL_SERVER_ERROR
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Manejar errores de validación
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        logger.warn("Validation error: {}", ex.getMessage());

        StringBuilder errorMessage = new StringBuilder("Error de validación: ");
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errorMessage.append(error.getField())
                        .append(" - ")
                        .append(error.getDefaultMessage())
                        .append("; ");
        });

        Map<String, Object> response = createErrorResponse(
            errorMessage.toString(),
            "VALIDATION_ERROR",
            HttpStatus.BAD_REQUEST
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Manejar errores de archivo demasiado grande
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        logger.warn("File size exceeded: {}", ex.getMessage());

        Map<String, Object> response = createErrorResponse(
            "El archivo excede el tamaño máximo permitido (50MB)",
            "FILE_SIZE_EXCEEDED",
            HttpStatus.BAD_REQUEST
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Manejar argumentos ilegales
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        logger.warn("Illegal argument: {}", ex.getMessage());

        Map<String, Object> response = createErrorResponse(
            "Parámetro inválido: " + ex.getMessage(),
            "INVALID_ARGUMENT",
            HttpStatus.BAD_REQUEST
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Manejar excepciones no controladas
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);

        Map<String, Object> response = createErrorResponse(
            "Error interno del servidor. Por favor, contacta al administrador.",
            "INTERNAL_SERVER_ERROR",
            HttpStatus.INTERNAL_SERVER_ERROR
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Crear respuesta de error estandarizada
     */
    private Map<String, Object> createErrorResponse(String message, String errorCode, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("errorCode", errorCode);
        response.put("status", status.value());
        response.put("timestamp", LocalDateTime.now());
        response.put("service", "ms-chatbot");

        return response;
    }
}