package com.mobydigital.chatbot.config;

import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PineconeConfig {

    private static final Logger logger = LoggerFactory.getLogger(PineconeConfig.class);

    @Value("${chatbot.pinecone.api-key}")
    private String pineconeApiKey;

    @Value("${chatbot.pinecone.index-name:moby-documents}")
    private String indexName;

    @Value("${chatbot.pinecone.environment:us-east1-aws}")
    private String environment;

    @Bean
    public Pinecone pineconeClient() {
        try {
            logger.info("Initializing Pinecone client for index: {}", indexName);

            // Usar el patrón correcto de la documentación v5.0.0
            Pinecone pinecone = new Pinecone.Builder(pineconeApiKey)
                .withSourceTag("moby_chatbot")
                .build();

            logger.info("Pinecone client initialized successfully");
            return pinecone;

        } catch (Exception e) {
            logger.error("Failed to initialize Pinecone client: {}", e.getMessage(), e);
            throw new RuntimeException("Could not initialize Pinecone client", e);
        }
    }

    @Bean
    public Index pineconeIndex(Pinecone pineconeClient) {
        try {
            logger.info("Getting Pinecone index connection: {}", indexName);

            // Usar el método correcto para obtener conexión al índice
            Index index = pineconeClient.getIndexConnection(indexName);

            logger.info("Pinecone index connection established successfully");
            return index;

        } catch (Exception e) {
            logger.error("Failed to get Pinecone index connection: {}", e.getMessage(), e);
            throw new RuntimeException("Could not connect to Pinecone index", e);
        }
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    }

    // Getters para configuración
    public String getIndexName() {
        return indexName;
    }

    public String getApiKey() {
        return pineconeApiKey;
    }

    public String getEnvironment() {
        return environment;
    }
}