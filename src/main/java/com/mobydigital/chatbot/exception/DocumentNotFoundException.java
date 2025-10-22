package com.mobydigital.chatbot.exception;

/**
 * Excepción cuando un documento no se encuentra
 */
public class DocumentNotFoundException extends ChatbotException {

    public DocumentNotFoundException(String message) {
        super(message, "DOCUMENT_NOT_FOUND");
    }

    public DocumentNotFoundException(Long documentId) {
        super("Documento con ID " + documentId + " no encontrado", "DOCUMENT_NOT_FOUND");
    }
}