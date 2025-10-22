package com.mobydigital.chatbot.controller;

import com.mobydigital.chatbot.dto.ChatRequestDTO;
import com.mobydigital.chatbot.dto.ChatResponseDTO;
import com.mobydigital.chatbot.service.RAGService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:8086", "https://test-api-google.netlify.app"})
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final RAGService ragService;

    @Autowired
    public ChatController(RAGService ragService) {
        this.ragService = ragService;
    }

    /**
     * Endpoint principal para enviar mensajes al chatbot
     */
    @PostMapping("/message")
    public ResponseEntity<ChatResponseDTO> sendMessage(
            @Valid @RequestBody ChatRequestDTO request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        try {
            // Extraer informacion del usuario del header de autorizacion
            String userId = extractUserFromAuth(authHeader);

            logger.info("Received chat message from user: {}", userId);

            // Validar la pregunta
            if (!ragService.isValidQuestion(request.getQuestion())) {
                return ResponseEntity.badRequest()
                    .body(ChatResponseDTO.error("La pregunta debe tener al menos 3 caracteres y contener texto válido."));
            }

            // Procesar la pregunta usando RAG
            ChatResponseDTO response = ragService.processQuestion(request, userId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

        } catch (Exception e) {
            logger.error("Error processing chat message", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ChatResponseDTO.error("Error interno del servidor. Por favor, intenta nuevamente"));
        }
    }

    /**
     * Obtener sugerencias de preguntas
     */
    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> getSuggestions() {

        try {
            List<String> suggestions = ragService.getSuggestedQuestions();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("suggestions", suggestions);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting suggestions", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Error al obtener sugerencias");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Health check del sistema de chat
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            RAGService.RAGStats stats = ragService.getStats();

            Map<String, Object> healthResponse = new HashMap<>();
            healthResponse.put("healthy", stats.isHealthy());
            healthResponse.put("status", stats.getStatus());
            healthResponse.put("totalVectors", stats.getTotalVectors());
            healthResponse.put("service", "ms-chatbot");

            HttpStatus status = stats.isHealthy() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(healthResponse);

        } catch (Exception e) {
            logger.error("Error in health check", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("healthy", false);
            errorResponse.put("status", "Error en health check");
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }


    /**
     * Endpoint para testear conectividad (sin autenticación requerida en Gateway)
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "MobyBot está funcionando correctamente");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * Extraer información del usuario desde el header Authorization
     * El API Gateway inyecta el token de Google, podríamos extraer info del usuario
     */
    private String extractUserFromAuth(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Por ahora retornamos un placeholder
            // En el futuro podríamos decodificar el JWT o hacer una llamada al servicio de usuario
            return "authenticated_user";
        }
        return "anonymous";
    }
}