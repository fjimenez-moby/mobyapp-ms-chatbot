package com.mobydigital.chatbot.service;

import com.mobydigital.chatbot.model.Document;
import com.mobydigital.chatbot.repository.DocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class PdfProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(PdfProcessingService.class);

    private final DocumentRepository documentRepository;
    private final GeminiService geminiService;
    private final VectorService vectorService;

    // Configuración de chunking
    private static final int MAX_CHUNK_SIZE = 1000; // caracteres
    private static final int CHUNK_OVERLAP = 100;   // caracteres de solapamiento
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]+\\s+");

    @Autowired
    public PdfProcessingService(DocumentRepository documentRepository,
                               GeminiService geminiService,
                               VectorService vectorService) {
        this.documentRepository = documentRepository;
        this.geminiService = geminiService;
        this.vectorService = vectorService;
    }

    public static class ProcessingResult {
        private final boolean success;
        private final String message;
        private final Document document;
        private final int chunksProcessed;

        public ProcessingResult(boolean success, String message, Document document, int chunksProcessed) {
            this.success = success;
            this.message = message;
            this.document = document;
            this.chunksProcessed = chunksProcessed;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Document getDocument() { return document; }
        public int getChunksProcessed() { return chunksProcessed; }
    }

    /**
     * Procesar un archivo PDF completo
     */
    public ProcessingResult processDocument(Document document, File pdfFile) {
        try {
            logger.info("Starting processing of document: {}", document.getOriginalName());

            // Actualizar estado a PROCESSING
            document.setStatus(Document.ProcessingStatus.PROCESSING);
            documentRepository.save(document);

            // Extraer texto del PDF
            String extractedText = extractTextFromPdf(pdfFile);
            if (extractedText == null || extractedText.trim().isEmpty()) {
                return fail(document, "No se pudo extraer texto del PDF");
            }

            logger.debug("Extracted {} characters from PDF", extractedText.length());

            // Dividir en chunks
            List<String> chunks = createChunks(extractedText);
            logger.debug("Created {} chunks from document", chunks.size());

            // Procesar cada chunk
            int successfulChunks = 0;
            for (int i = 0; i < chunks.size(); i++) {
                if (processChunk(document, i, chunks.get(i))) {
                    successfulChunks++;
                }
            }

            if (successfulChunks == 0) {
                return fail(document, "No se pudo procesar ningún chunk del documento");
            }

            // Actualizar estado a COMPLETED
            document.setStatus(Document.ProcessingStatus.COMPLETED);
            documentRepository.save(document);

            logger.info("Successfully processed document {} with {}/{} chunks",
                       document.getOriginalName(), successfulChunks, chunks.size());

            return new ProcessingResult(true,
                                      String.format("Documento procesado exitosamente (%d/%d chunks)",
                                                   successfulChunks, chunks.size()),
                                      document,
                                      successfulChunks);

        } catch (Exception e) {
            logger.error("Error processing document {}: {}", document.getOriginalName(), e.getMessage(), e);
            return fail(document, "Error interno al procesar el documento: " + e.getMessage());
        }
    }

    /**
     * Re-procesar un documento existente
     */
    public ProcessingResult reprocessDocument(Long documentId) {
        Optional<Document> documentOpt = documentRepository.findById(documentId);
        if (documentOpt.isEmpty()) {
            return new ProcessingResult(false, "Documento no encontrado", null, 0);
        }

        Document document = documentOpt.get();

        // Eliminar vectores existentes
        vectorService.deleteDocumentVectors(document.getId().toString());

        // Obtener archivo desde el path almacenado
        File pdfFile = new File(document.getFilePath());
        if (!pdfFile.exists()) {
            return fail(document, "Archivo PDF no encontrado en: " + document.getFilePath());
        }

        return processDocument(document, pdfFile);
    }

    /**
     * Extraer texto de un archivo PDF
     */
    private String extractTextFromPdf(File pdfFile) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();

            // Configurar el stripper
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");

            // Extraer texto de todas las páginas
            String text = stripper.getText(document);

            // Limpiar el texto
            return cleanExtractedText(text);

        } catch (IOException e) {
            logger.error("Error extracting text from PDF {}: {}", pdfFile.getName(), e.getMessage());
            throw e;
        }
    }

    /**
     * Limpiar texto extraído
     */
    private String cleanExtractedText(String text) {
        if (text == null) return "";

        return text
            // Normalizar espacios en blanco
            .replaceAll("\\s+", " ")
            // Eliminar caracteres de control excepto saltos de línea
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
            // Normalizar saltos de línea múltiples
            .replaceAll("\n{3,}", "\n\n")
            .trim();
    }

    /**
     * Dividir texto en chunks
     */
    private List<String> createChunks(String text) {
        List<String> chunks = new ArrayList<>();

        if (text.length() <= MAX_CHUNK_SIZE) {
            chunks.add(text);
            return chunks;
        }

        // Dividir por oraciones primero
        String[] sentences = SENTENCE_PATTERN.split(text);
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            // Si agregar esta oración excede el tamaño máximo
            if (currentChunk.length() + sentence.length() > MAX_CHUNK_SIZE) {
                // Guardar chunk actual si no está vacío
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());

                    // Preparar siguiente chunk con overlap
                    String overlap = getOverlap(currentChunk.toString());
                    currentChunk = new StringBuilder(overlap);
                }
            }

            // Agregar oración al chunk actual
            if (currentChunk.length() > 0) {
                currentChunk.append(" ");
            }
            currentChunk.append(sentence);
        }

        // Agregar último chunk si no está vacío
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * Obtener overlap del final de un chunk
     */
    private String getOverlap(String chunk) {
        if (chunk.length() <= CHUNK_OVERLAP) {
            return chunk;
        }

        String overlap = chunk.substring(chunk.length() - CHUNK_OVERLAP);

        // Intentar encontrar un punto de corte natural (inicio de oración)
        int spaceIndex = overlap.indexOf(' ');
        if (spaceIndex > 0) {
            overlap = overlap.substring(spaceIndex + 1);
        }

        return overlap;
    }

    /**
     * Procesar un chunk individual
     */
    private boolean processChunk(Document document, int chunkIndex, String chunkText) {
        try {
            // Generar embedding para el chunk
            List<Double> embedding = geminiService.generateEmbedding(chunkText);
            if (embedding == null || embedding.isEmpty()) {
                logger.warn("Could not generate embedding for chunk {} of document {}",
                           chunkIndex, document.getId());
                return false;
            }

            // Crear metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("document_id", document.getId().toString());
            metadata.put("document_name", document.getOriginalName());
            metadata.put("category", document.getCategory());
            metadata.put("chunk_index", String.valueOf(chunkIndex));
            metadata.put("uploaded_by", document.getUploadedBy());

            // Almacenar en vector database
            String chunkId = "chunk_" + chunkIndex;
            boolean stored = vectorService.storeVector(
                document.getId().toString(),
                chunkId,
                chunkText,
                embedding,
                metadata
            );

            if (!stored) {
                logger.warn("Could not store vector for chunk {} of document {}",
                           chunkIndex, document.getId());
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.error("Error processing chunk {} of document {}: {}",
                        chunkIndex, document.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Calcular hash del archivo para detectar duplicados
     */
    public String calculateFileHash(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (var fis = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }

            byte[] hashBytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (Exception e) {
            logger.error("Error calculating file hash: {}", e.getMessage(), e);
            return UUID.randomUUID().toString(); // Fallback
        }
    }

    /**
     * Marcar documento como fallido
     */
    private ProcessingResult fail(Document document, String message) {
        document.setStatus(Document.ProcessingStatus.FAILED);
        documentRepository.save(document);
        logger.error("Document processing failed for {}: {}", document.getOriginalName(), message);
        return new ProcessingResult(false, message, document, 0);
    }
}