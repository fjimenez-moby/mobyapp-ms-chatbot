package com.mobydigital.chatbot.controller;

import com.mobydigital.chatbot.dto.DocumentDTO;
import com.mobydigital.chatbot.model.Document;
import com.mobydigital.chatbot.repository.DocumentRepository;
import com.mobydigital.chatbot.service.FileStorageService;
import com.mobydigital.chatbot.service.PdfProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = {"http://localhost:4200", "https://test-api-google.netlify.app"})
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final PdfProcessingService pdfProcessingService;

    @Autowired
    public DocumentController(DocumentRepository documentRepository,
                             FileStorageService fileStorageService,
                             PdfProcessingService pdfProcessingService) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.pdfProcessingService = pdfProcessingService;
    }

    /**
     * Subir y procesar un nuevo documento PDF
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestHeader("Authorization") String authHeader) {

        try {
            String uploadedBy = extractUserFromAuth(authHeader);
            logger.info("Upload request from user: {}", uploadedBy);

            // Almacenar archivo
            FileStorageService.StorageResult storageResult = fileStorageService.storeFile(file);
            if (!storageResult.isSuccess()) {
                return ResponseEntity.badRequest().body(createErrorResponse(storageResult.getMessage()));
            }

            // Calcular hash del archivo
            File storedFile = fileStorageService.getFile(storageResult.getFilePath());
            String fileHash = pdfProcessingService.calculateFileHash(storedFile);

            // Verificar si ya existe un documento con el mismo hash
            Optional<Document> existingDoc = documentRepository.findByFileHash(fileHash);
            if (existingDoc.isPresent()) {
                // Eliminar archivo duplicado
                fileStorageService.deleteFile(storageResult.getFilePath());
                return ResponseEntity.badRequest().body(createErrorResponse("Este documento ya existe en el sistema"));
            }

            // Crear entidad Document
            Document document = new Document();
            document.setFileName(storageResult.getFileName());
            document.setOriginalName(storageResult.getOriginalName());
            document.setCategory(category != null ? category : "GENERAL"); // Categoría opcional
            document.setDescription(description);
            document.setUploadedBy(uploadedBy);
            document.setFileHash(fileHash);
            document.setFileSize(storageResult.getFileSize());
            document.setFilePath(storageResult.getFilePath());
            document.setMimeType("application/pdf");
            document.setStatus(Document.ProcessingStatus.UPLOADED);

            // Guardar en base de datos
            logger.info("Saving document to database: {}", document.getFileName());
            document = documentRepository.save(document);
            logger.info("Document saved successfully with ID: {}", document.getId());

            // Procesar documento de forma asíncrona (en este caso sincrónica para simplificar)
            logger.info("Starting PDF processing for document: {}", document.getFileName());
            PdfProcessingService.ProcessingResult processingResult =
                pdfProcessingService.processDocument(document, storedFile);
            logger.info("PDF processing completed. Success: {}, Message: {}",
                processingResult.isSuccess(), processingResult.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", processingResult.isSuccess());
            response.put("message", processingResult.getMessage());
            response.put("document", DocumentDTO.fromEntity(processingResult.getDocument()));
            response.put("chunksProcessed", processingResult.getChunksProcessed());

            HttpStatus status = processingResult.isSuccess() ? HttpStatus.CREATED : HttpStatus.PARTIAL_CONTENT;
            return ResponseEntity.status(status).body(response);

        } catch (Exception e) {
            logger.error("Error uploading document: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Error interno al subir el documento: " + e.getMessage()));
        }
    }

    /**
     * Listar todos los documentos
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllDocuments(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {

        try {
            List<Document> documents;

            if (category != null && !category.isEmpty()) {
                documents = documentRepository.findByCategory(category.toUpperCase());
            } else if (status != null && !status.isEmpty()) {
                Document.ProcessingStatus processStatus = Document.ProcessingStatus.valueOf(status.toUpperCase());
                documents = documentRepository.findByStatus(processStatus);
            } else {
                documents = documentRepository.findAll();
            }

            List<DocumentDTO> documentDTOs = documents.stream()
                .map(DocumentDTO::fromEntity)
                .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("documents", documentDTOs);
            response.put("total", documentDTOs.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting documents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Error al obtener documentos"));
        }
    }

    /**
     * Obtener documento por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDocumentById(@PathVariable Long id) {
        try {
            Optional<Document> documentOpt = documentRepository.findById(id);

            if (documentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("Documento no encontrado"));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("document", DocumentDTO.fromEntity(documentOpt.get()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting document by id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Error al obtener documento"));
        }
    }

    /**
     * Eliminar documento
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable Long id,
                                                             @RequestHeader("Authorization") String authHeader) {
        try {
            String userId = extractUserFromAuth(authHeader);
            Optional<Document> documentOpt = documentRepository.findById(id);

            if (documentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("Documento no encontrado"));
            }

            Document document = documentOpt.get();

            // Eliminar vectores de ChromaDB
            // TODO: Verificar permisos de eliminación (por ahora cualquier usuario autenticado puede eliminar)

            // Eliminar archivo físico
            fileStorageService.deleteFile(document.getFilePath());

            // Eliminar de base de datos
            documentRepository.delete(document);

            logger.info("Document {} deleted by user {}", document.getOriginalName(), userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Documento eliminado exitosamente");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error deleting document with id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Error al eliminar documento"));
        }
    }

    /**
     * Re-procesar documento
     */
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<Map<String, Object>> reprocessDocument(@PathVariable Long id,
                                                                @RequestHeader("Authorization") String authHeader) {
        try {
            String userId = extractUserFromAuth(authHeader);
            logger.info("Reprocess request for document {} by user {}", id, userId);

            PdfProcessingService.ProcessingResult result = pdfProcessingService.reprocessDocument(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            if (result.getDocument() != null) {
                response.put("document", DocumentDTO.fromEntity(result.getDocument()));
            }
            response.put("chunksProcessed", result.getChunksProcessed());

            HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(response);

        } catch (Exception e) {
            logger.error("Error reprocessing document with id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Error al re-procesar documento"));
        }
    }

    /**
     * Obtener estadísticas de documentos
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDocumentStats() {
        try {
            List<Object[]> categoryStats = documentRepository.countByCategory();

            Map<String, Object> stats = new HashMap<>();
            stats.put("success", true);
            stats.put("totalDocuments", documentRepository.count());

            Map<String, Long> byCategory = new HashMap<>();
            for (Object[] stat : categoryStats) {
                byCategory.put((String) stat[0], ((Number) stat[1]).longValue());
            }
            stats.put("byCategory", byCategory);

            Map<String, Long> byStatus = new HashMap<>();
            for (Document.ProcessingStatus status : Document.ProcessingStatus.values()) {
                long count = documentRepository.findByStatus(status).size();
                byStatus.put(status.name(), count);
            }
            stats.put("byStatus", byStatus);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Error getting document stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Error al obtener estadísticas"));
        }
    }

    // Métodos auxiliares

    private String extractUserFromAuth(String authHeader) {
        // Por ahora retornamos un placeholder
        // TODO: Implementar extracción real del usuario desde el token
        return "admin_user";
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("timestamp", LocalDateTime.now());
        return response;
    }
}