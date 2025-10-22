package com.mobydigital.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDTO {

    @NotBlank(message = "La pregunta no puede estar vacía")
    @Size(max = 1000, message = "La pregunta no puede exceder 1000 caracteres")
    private String question;

    // Sin filtro por categoría - el RAG buscará en todos los documentos
}