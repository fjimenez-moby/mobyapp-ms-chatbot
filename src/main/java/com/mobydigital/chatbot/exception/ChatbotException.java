package com.mobydigital.chatbot.exception;

/**
 * Excepción base para el microservicio de chatbot
 */
public class ChatbotException extends Exception {

    private final String errorCode;

    public ChatbotException(String message) {
        super(message);
        this.errorCode = "CHATBOT_ERROR";
    }

    public ChatbotException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ChatbotException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "CHATBOT_ERROR";
    }

    public ChatbotException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}