package com.mobydigital.chatbot.service;

import com.mobydigital.chatbot.dto.ChatRequestDTO;
import com.mobydigital.chatbot.dto.ChatResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RAGService {

    private static final Logger logger = LoggerFactory.getLogger(RAGService.class);

    private final GeminiService geminiService;
    private final VectorService vectorService;

    // Configuración RAG
    private static final int MAX_CONTEXT_CHUNKS = 5;
    private static final double MIN_SIMILARITY_THRESHOLD = 0.6;
    private static final int MAX_CONTEXT_LENGTH = 4000; // caracteres

    @Autowired
    public RAGService(GeminiService geminiService, VectorService vectorService) {
        this.geminiService = geminiService;
        this.vectorService = vectorService;
    }

    /**
     * Procesar pregunta del usuario y generar respuesta usando RAG
     */
    public ChatResponseDTO processQuestion(ChatRequestDTO request, String userId) {
        try {
            logger.info("Processing question from user {}: {}", userId, request.getQuestion());

            // 1. Generar embedding de la pregunta
            List<Double> queryEmbedding = geminiService.generateEmbedding(request.getQuestion());

            if (queryEmbedding == null) {
                logger.error("Failed to generate embedding for question: {}", request.getQuestion());
                return ChatResponseDTO.error("No pude procesar tu pregunta. Por favor, intenta nuevamente.");
            }

            // 2. Buscar chunks relevantes usando el embedding
            List<VectorService.SearchResult> searchResults = vectorService.searchSimilar(
                queryEmbedding,
                MAX_CONTEXT_CHUNKS
            );

            // Log detallado de resultados RAG
            logger.info("=== RAG SEARCH RESULTS for question: '{}' ===", request.getQuestion());
            for (int i = 0; i < searchResults.size(); i++) {
                VectorService.SearchResult result = searchResults.get(i);
                String preview = result.getText().length() > 100
                    ? result.getText().substring(0, 100) + "..."
                    : result.getText();
                logger.info("Result {}: ID={}, Similarity={}, Preview='{}'",
                    i+1, result.getId(), String.format("%.3f", result.getSimilarity()), preview);
            }
            logger.info("=== END RAG RESULTS ===");

            // 3. Verificar si encontramos información relevante
            if (searchResults.isEmpty()) {
                logger.info("No relevant documents found for question: {}", request.getQuestion());
                return generateFallbackResponse(request.getQuestion());
            }

            // 4. Filtrar por threshold de similitud
            List<VectorService.SearchResult> relevantResults = searchResults.stream()
                .filter(result -> result.getSimilarity() >= MIN_SIMILARITY_THRESHOLD)
                .collect(Collectors.toList());

            // Si no hay resultados con alta similitud, usar todos los resultados disponibles
            if (relevantResults.isEmpty()) {
                logger.info("Using all available results as none meet similarity threshold");
                relevantResults = searchResults;
            }

            // 5. Construir contexto desde los chunks relevantes
            String context = buildContext(relevantResults);
            List<String> sourceDocuments = extractSourceDocuments(relevantResults);

            logger.info("=== FINAL CONTEXT for Gemini ===");
            logger.info("Context length: {} characters from {} sources", context.length(), sourceDocuments.size());
            logger.info("Context preview (first 300 chars): '{}'",
                context.length() > 300 ? context.substring(0, 300) + "..." : context);
            logger.info("=== END CONTEXT ===");

            // 6. Generar respuesta con Gemini usando el contexto
            String response = geminiService.generateChatResponse(request.getQuestion(), context);

            if (response == null || response.trim().isEmpty()) {
                logger.error("Gemini returned empty response for question: {}", request.getQuestion());
                return ChatResponseDTO.error("No pude generar una respuesta. Por favor, intenta nuevamente.");
            }

            logger.info("Successfully generated response for user {} (sources: {})",
                       userId, sourceDocuments.size());

            return ChatResponseDTO.success(response, sourceDocuments);

        } catch (Exception e) {
            logger.error("Error processing question from user {}: {}", userId, e.getMessage(), e);
            return ChatResponseDTO.error("Ocurrió un error interno. Por favor, intenta más tarde.");
        }
    }

    /**
     * Generar respuesta cuando no hay información relevante
     */
    private ChatResponseDTO generateFallbackResponse(String question) {
        String fallbackResponse = String.format(
            "No encontré información específica sobre tu consulta: \"%s\".\n\n" +
            "Te sugiero:\n" +
            "• Reformular tu pregunta con términos diferentes\n" +
            "• Contactar directamente a Recursos Humanos\n" +
            "• Revisar el manual del empleado si está disponible\n\n" +
            "¿Hay algo más específico en lo que pueda ayudarte?",
            question
        );

        return ChatResponseDTO.success(fallbackResponse, new ArrayList<>());
    }

    /**
     * Construir contexto desde los chunks relevantes
     */
    private String buildContext(List<VectorService.SearchResult> results) {
        StringBuilder contextBuilder = new StringBuilder();
        int currentLength = 0;

        for (VectorService.SearchResult result : results) {
            String chunkText = result.getText();
            String documentName = result.getMetadata().get("document_name");

            // Crear entrada de contexto con fuente
            String contextEntry = String.format(
                "[Fuente: %s]\n%s\n\n",
                documentName != null ? documentName : "Documento",
                chunkText
            );

            // Verificar si agregarlo excedería el límite
            if (currentLength + contextEntry.length() > MAX_CONTEXT_LENGTH) {
                break;
            }

            contextBuilder.append(contextEntry);
            currentLength += contextEntry.length();
        }

        return contextBuilder.toString().trim();
    }

    /**
     * Extraer nombres de documentos fuente únicos
     */
    private List<String> extractSourceDocuments(List<VectorService.SearchResult> results) {
        return results.stream()
                     .map(result -> result.getMetadata().get("document_name"))
                     .filter(name -> name != null && !name.isEmpty())
                     .distinct()
                     .collect(Collectors.toList());
    }

    /**
     * Validar pregunta del usuario
     */
    public boolean isValidQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            return false;
        }

        // Verificar longitud mínima
        if (question.trim().length() < 3) {
            return false;
        }

        // Verificar que no sea solo números o caracteres especiales
        if (!question.matches(".*[a-zA-ZáéíóúÁÉÍÓÚñÑ].*")) {
            return false;
        }

        return true;
    }

    /**
     * Obtener sugerencias de preguntas generales
     */
    public List<String> getSuggestedQuestions() {
        List<String> suggestions = new ArrayList<>();

        // Sugerencias generales basadas en consultas típicas empresariales
        suggestions.add("¿Cuántos días de vacaciones tengo?");
        suggestions.add("¿Cómo solicito una licencia?");
        suggestions.add("¿Qué beneficios tengo disponibles?");
        suggestions.add("¿Cuál es la política de home office?");
        suggestions.add("¿Puedo usar equipos personales para trabajar?");
        suggestions.add("¿Cuáles son las políticas de seguridad?");
        suggestions.add("¿Qué necesito hacer en mi primer día?");
        suggestions.add("¿Cómo funciona el proceso de evaluación?");

        return suggestions;
    }

    /**
     * Obtener estadísticas del sistema RAG
     */
    public RAGStats getStats() {
        try {
            var vectorStats = vectorService.getStats();

            return new RAGStats(
                (Integer) vectorStats.getOrDefault("totalVectors", 0),
                vectorService.isHealthy(),
                "Sistema operativo"
            );

        } catch (Exception e) {
            logger.error("Error getting RAG stats: {}", e.getMessage(), e);
            return new RAGStats(0, false, "Error al obtener estadísticas");
        }
    }

    public static class RAGStats {
        private final int totalVectors;
        private final boolean healthy;
        private final String status;

        public RAGStats(int totalVectors, boolean healthy, String status) {
            this.totalVectors = totalVectors;
            this.healthy = healthy;
            this.status = status;
        }

        public int getTotalVectors() { return totalVectors; }
        public boolean isHealthy() { return healthy; }
        public String getStatus() { return status; }
    }
}