package com.mobydigital.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDTO {

    private boolean success;
    private String message;
    private String response;
    private LocalDateTime timestamp;
    private List<String> sourceDocuments;
    private String error;

    // Constructor para respuesta exitosa
    public static ChatResponseDTO success(String response, List<String> sources) {
        return new ChatResponseDTO(
            true,
            "Respuesta generada exitosamente",
            response,
            LocalDateTime.now(),
            sources,
            null
        );
    }

    // Constructor para respuesta con error
    public static ChatResponseDTO error(String errorMessage) {
        return new ChatResponseDTO(
            false,
            "Error al procesar la consulta",
            null,
            LocalDateTime.now(),
            null,
            errorMessage
        );
    }
}