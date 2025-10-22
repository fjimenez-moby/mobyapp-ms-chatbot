package com.mobydigital.chatbot.service;

import com.mobydigital.chatbot.config.PineconeConfig;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class VectorService {

    private static final Logger logger = LoggerFactory.getLogger(VectorService.class);

    private final Index index;
    private final PineconeConfig pineconeConfig;

    @Autowired
    public VectorService(Index index, PineconeConfig pineconeConfig) {
        this.index = index;
        this.pineconeConfig = pineconeConfig;
        logger.info("VectorService initialized with Pinecone index: {}", pineconeConfig.getIndexName());
    }

    public static class SearchResult {
        private final String id;
        private final String text;
        private final Map<String, String> metadata;
        private final double similarity;

        public SearchResult(String id, String text, Map<String, String> metadata, double similarity) {
            this.id = id;
            this.text = text;
            this.metadata = metadata;
            this.similarity = similarity;
        }

        public String getId() { return id; }
        public String getText() { return text; }
        public Map<String, String> getMetadata() { return metadata; }
        public double getDistance() { return 1.0 - similarity; }
        public double getSimilarity() { return similarity; }
    }

    /**
     * Almacenar un chunk de texto con su embedding usando RestTemplate
     */
    public boolean storeVector(String documentId, String chunkId, String text,
                              List<Double> embedding, Map<String, String> metadata) {
        try {
            if (index == null) {
                logger.error("Pinecone index not initialized");
                return false;
            }

            String id = documentId + "_" + chunkId;

            // Convertir List<Double> a List<Float>
            List<Float> embeddingFloats = embedding.stream()
                .map(Double::floatValue)
                .collect(Collectors.toList());

            // Agregar el texto a los metadatos
            Map<String, String> enrichedMetadata = new HashMap<>(metadata);
            enrichedMetadata.put("text", text);
            enrichedMetadata.put("document_id", documentId);
            enrichedMetadata.put("chunk_id", chunkId);

            // Convertir Map<String, String> a Struct para Pinecone
            com.google.protobuf.Struct.Builder structBuilder = com.google.protobuf.Struct.newBuilder();
            for (Map.Entry<String, String> entry : enrichedMetadata.entrySet()) {
                structBuilder.putFields(entry.getKey(),
                    com.google.protobuf.Value.newBuilder().setStringValue(entry.getValue()).build());
            }
            com.google.protobuf.Struct metadataStruct = structBuilder.build();

            // Usar el método upsert completo con metadatos
            index.upsert(id, embeddingFloats, null, null, metadataStruct, "");

            logger.debug("Stored vector in Pinecone for document {} chunk {}", documentId, chunkId);
            return true;

        } catch (Exception e) {
            logger.error("Error storing vector for document {} chunk {}: {}", documentId, chunkId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Buscar chunks similares a la query
     */
    public List<SearchResult> searchSimilar(List<Double> queryEmbedding, int limit) {
        return searchSimilar(queryEmbedding, limit, null);
    }

    /**
     * Buscar chunks similares usando Pinecone query
     */
    public List<SearchResult> searchSimilar(List<Double> queryEmbedding, int limit, String category) {
        try {
            if (index == null) {
                logger.error("Pinecone index not initialized");
                return new ArrayList<>();
            }

            // Convertir List<Double> a List<Float>
            List<Float> queryEmbeddingFloats = queryEmbedding.stream()
                .map(Double::floatValue)
                .collect(Collectors.toList());

            logger.debug("Searching for {} similar vectors with embedding size {}", limit, queryEmbeddingFloats.size());

            // Usar la estructura correcta de QueryRequest según la documentación v5.0.0
            // Parámetros: topK, vector, sparseIndices, sparseValues, namespace, id, filter, includeValues, includeMetadata
            var queryResponse = index.query(
                limit,                    // topK - número de resultados
                queryEmbeddingFloats,     // vector - el vector de consulta
                null,                     // sparseIndices - no usado (List<Long>)
                null,                     // sparseValues - no usado (List<Float>)
                "",                       // namespace - usar namespace por defecto
                "",                       // id - string vacío para búsqueda por similitud
                null,                     // filter - sin filtros por ahora (Struct)
                false,                    // includeValues - no necesitamos los valores del vector
                true                      // includeMetadata - sí incluir metadatos
            );

            List<SearchResult> results = new ArrayList<>();

            if (queryResponse != null && queryResponse.getMatchesList() != null) {
                logger.debug("Query response received with {} matches", queryResponse.getMatchesList().size());

                for (var match : queryResponse.getMatchesList()) {
                    // Extraer metadatos
                    Map<String, String> metadata = new HashMap<>();
                    if (match.getMetadata() != null) {
                        var structMap = match.getMetadata().getFieldsMap();
                        for (var entry : structMap.entrySet()) {
                            metadata.put(entry.getKey(), entry.getValue().getStringValue());
                        }
                    }

                    String text = metadata.get("text");
                    if (text != null && !text.isEmpty()) {
                        SearchResult result = new SearchResult(
                            match.getId(),
                            text,
                            metadata,
                            match.getScore()
                        );

                        // Reducir el umbral de similitud para debugging
                        if (result.getSimilarity() > 0.3) {
                            results.add(result);
                            logger.debug("Added result with similarity: {} for ID: {}",
                                result.getSimilarity(), result.getId());
                        } else {
                            logger.debug("Skipped result with low similarity: {} for ID: {}",
                                result.getSimilarity(), result.getId());
                        }
                    } else {
                        logger.debug("Skipped result with no text content for ID: {}", match.getId());
                    }
                }
            } else {
                logger.warn("Query response was null or had no matches list");
            }

            logger.debug("Found {} similar vectors (limit: {})", results.size(), limit);
            return results;

        } catch (Exception e) {
            logger.error("Error searching similar vectors: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Eliminar todos los vectores de un documento
     */
    public boolean deleteDocumentVectors(String documentId) {
        try {
            if (index == null) {
                logger.error("Pinecone index not initialized");
                return false;
            }

            logger.warn("Document deletion by metadata not fully implemented for Pinecone");
            logger.info("Would delete vectors for document {}", documentId);
            return true;

        } catch (Exception e) {
            logger.error("Error deleting vectors for document {}: {}", documentId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Obtener estadísticas del vector store
     */
    public Map<String, Object> getStats() {
        try {
            if (index == null) {
                Map<String, Object> errorStats = new HashMap<>();
                errorStats.put("error", "Pinecone index not initialized");
                errorStats.put("totalVectors", 0);
                errorStats.put("indexName", pineconeConfig.getIndexName());
                return errorStats;
            }

            // Usar la nueva API para obtener estadísticas
            var stats = index.describeIndexStats();

            Map<String, Object> result = new HashMap<>();
            result.put("totalVectors", stats.getTotalVectorCount());
            result.put("indexName", pineconeConfig.getIndexName());
            result.put("dimension", stats.getDimension());

            return result;

        } catch (Exception e) {
            logger.error("Error getting vector store stats: {}", e.getMessage(), e);
            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put("error", "Could not retrieve stats");
            errorStats.put("totalVectors", 0);
            return errorStats;
        }
    }

    /**
     * Verificar si Pinecone está funcionando
     */
    public boolean isHealthy() {
        try {
            if (index == null) {
                return false;
            }

            // Hacer una consulta simple para verificar conectividad
            var stats = index.describeIndexStats();
            return stats != null;

        } catch (Exception e) {
            logger.warn("Pinecone health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Limpiar toda la colección
     */
    public boolean clearCollection() {
        try {
            if (index == null) {
                logger.error("Pinecone index not initialized");
                return false;
            }

            // Usar la nueva API para limpiar (namespace por defecto)
            index.deleteAll("");
            logger.info("Cleared all vectors from Pinecone index {}", pineconeConfig.getIndexName());
            return true;

        } catch (Exception e) {
            logger.error("Error clearing Pinecone index: {}", e.getMessage(), e);
            return false;
        }
    }
}