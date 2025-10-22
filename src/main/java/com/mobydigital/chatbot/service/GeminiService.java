package com.mobydigital.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    @Value("${chatbot.gemini.api-key}")
    private String apiKey;

    @Value("${chatbot.gemini.model:gemini-1.5-flash}")
    private String chatModel;

    @Value("${chatbot.gemini.embedding-model:text-embedding-004}")
    private String embeddingModel;

    @Value("${chatbot.gemini.temperature:0.3}")
    private Double temperature;

    @Value("${chatbot.gemini.max-tokens:1024}")
    private Integer maxTokens;

    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String GENERATE_CONTENT_ENDPOINT = "/models/{model}:generateContent";
    private static final String EMBED_CONTENT_ENDPOINT = "/models/{model}:embedContent";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generar respuesta de chat usando Gemini
     */
    public String generateChatResponse(String prompt, String context) {
        try {
            String url = GEMINI_API_BASE_URL +
                        GENERATE_CONTENT_ENDPOINT.replace("{model}", chatModel) +
                        "?key=" + apiKey;

            // Construir el prompt completo con contexto
            String fullPrompt = buildPromptWithContext(prompt, context);

            // Crear el body del request
            Map<String, Object> requestBody = createChatRequestBody(fullPrompt);

            // Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            logger.debug("Sending request to Gemini API: {}", url);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return extractTextFromResponse(response.getBody());
            } else {
                logger.error("Error response from Gemini API: {}", response.getStatusCode());
                return "Lo siento, hubo un error al procesar tu consulta. Por favor, intenta nuevamente.";
            }

        } catch (Exception e) {
            logger.error("Error calling Gemini API", e);
            return "Lo siento, no pude procesar tu consulta en este momento. Por favor, intenta más tarde.";
        }
    }

    /**
     * Generar embeddings para texto
     */
    public List<Double> generateEmbedding(String text) {
        try {
            String url = GEMINI_API_BASE_URL +
                        EMBED_CONTENT_ENDPOINT.replace("{model}", embeddingModel) +
                        "?key=" + apiKey;

            Map<String, Object> requestBody = createEmbeddingRequestBody(text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            logger.debug("Generating embedding for text of length: {}", text.length());

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return extractEmbeddingFromResponse(response.getBody());
            } else {
                logger.error("Error generating embedding: {}", response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            logger.error("Error generating embedding", e);
            return null;
        }
    }

    private String buildPromptWithContext(String userQuestion, String context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Eres MobyBot, un asistente virtual de MobyDigital. ");
        prompt.append("Tu función es ayudar a los empleados de la empresa respondiendo preguntas sobre ");
        prompt.append("políticas, procedimientos, beneficios y documentación interna.\n\n");

        prompt.append("INSTRUCCIONES:\n");
        prompt.append("- Responde en español argentino de manera profesional pero directa\n");
        prompt.append("- Si tienes información relevante en el contexto, úsala para responder\n");
        prompt.append("- Si no tienes información suficiente, sugiere contactar a RRHH\n");
        prompt.append("- Mantén las respuestas concisas pero completas\n");
        prompt.append("- NO incluyas saludos como 'Hola' al inicio de la respuesta\n");
        prompt.append("- NO menciones las fuentes ni nombres de documentos en la respuesta\n");
        prompt.append("- Responde directamente a la pregunta sin preámbulos\n\n");

        if (context != null && !context.trim().isEmpty()) {
            prompt.append("INFORMACIÓN RELEVANTE DE LOS DOCUMENTOS:\n");
            prompt.append(context);
            prompt.append("\n\n");
        }

        prompt.append("PREGUNTA DEL EMPLEADO:\n");
        prompt.append(userQuestion);
        prompt.append("\n\nRESPUESTA:");

        return prompt.toString();
    }

    private Map<String, Object> createChatRequestBody(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();

        // Contents array
        Map<String, Object> content = new HashMap<>();
        Map<String, String> part = new HashMap<>();
        part.put("text", prompt);
        content.put("parts", List.of(part));
        requestBody.put("contents", List.of(content));

        // Generation config
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", temperature);
        generationConfig.put("maxOutputTokens", maxTokens);
        requestBody.put("generationConfig", generationConfig);

        return requestBody;
    }

    private Map<String, Object> createEmbeddingRequestBody(String text) {
        Map<String, Object> requestBody = new HashMap<>();

        Map<String, Object> content = new HashMap<>();
        Map<String, String> part = new HashMap<>();
        part.put("text", text);
        content.put("parts", List.of(part));
        requestBody.put("content", content);

        return requestBody;
    }

    private String extractTextFromResponse(String responseBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            JsonNode candidates = jsonNode.get("candidates");

            if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                JsonNode candidate = candidates.get(0);
                JsonNode content = candidate.get("content");
                JsonNode parts = content.get("parts");

                if (parts != null && parts.isArray() && parts.size() > 0) {
                    JsonNode text = parts.get(0).get("text");
                    return text.asText();
                }
            }

            logger.warn("Could not extract text from Gemini response: {}", responseBody);
            return "No pude generar una respuesta válida. Por favor, intenta reformular tu pregunta.";

        } catch (Exception e) {
            logger.error("Error parsing Gemini response", e);
            return "Error al procesar la respuesta. Por favor, intenta nuevamente.";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Double> extractEmbeddingFromResponse(String responseBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            JsonNode embedding = jsonNode.get("embedding");
            JsonNode values = embedding.get("values");

            if (values != null && values.isArray()) {
                return objectMapper.convertValue(values, List.class);
            }

            logger.warn("Could not extract embedding from response: {}", responseBody);
            return null;

        } catch (Exception e) {
            logger.error("Error parsing embedding response", e);
            return null;
        }
    }
}