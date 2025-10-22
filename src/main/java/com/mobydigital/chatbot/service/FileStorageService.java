package com.mobydigital.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    // Directorio base para almacenar PDFs (configurable)
    @Value("${chatbot.storage.path:./storage/documents}")
    private String storageBasePath;

    // Extensiones permitidas
    private static final String[] ALLOWED_EXTENSIONS = {".pdf"};
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    /**
     * Inicializar el directorio de almacenamiento
     */
    private void initializeStorage() throws IOException {
        Path storagePath = Paths.get(storageBasePath);
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
            logger.info("Created storage directory: {}", storagePath.toAbsolutePath());
        }
    }

    /**
     * Almacenar archivo PDF
     */
    public StorageResult storeFile(MultipartFile file) {
        try {
            // Validar archivo
            ValidationResult validation = validateFile(file);
            if (!validation.isValid()) {
                return StorageResult.error(validation.getErrorMessage());
            }

            // Inicializar almacenamiento
            initializeStorage();

            // Generar nombre único para el archivo
            String originalFileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);
            String uniqueFileName = generateUniqueFileName(originalFileName, fileExtension);

            // Ruta completa del archivo (directamente en el directorio base)
            Path filePath = Paths.get(storageBasePath, uniqueFileName);

            // Copiar archivo
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            logger.info("Stored file: {} -> {}", originalFileName, filePath);

            return StorageResult.success(
                filePath.toString(),
                uniqueFileName,
                originalFileName,
                file.getSize()
            );

        } catch (IOException e) {
            logger.error("Error storing file {}: {}", file.getOriginalFilename(), e.getMessage(), e);
            return StorageResult.error("Error al almacenar el archivo: " + e.getMessage());
        }
    }

    /**
     * Obtener archivo por ruta
     */
    public File getFile(String filePath) {
        try {
            Path path = Paths.get(filePath);

            // Verificar que el archivo esté dentro del directorio base (seguridad)
            if (!path.toAbsolutePath().startsWith(Paths.get(storageBasePath).toAbsolutePath())) {
                logger.warn("Attempt to access file outside storage directory: {}", filePath);
                return null;
            }

            File file = path.toFile();
            if (file.exists() && file.isFile()) {
                return file;
            }

            logger.warn("File not found: {}", filePath);
            return null;

        } catch (Exception e) {
            logger.error("Error getting file {}: {}", filePath, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Eliminar archivo
     */
    public boolean deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);

            // Verificar seguridad
            if (!path.toAbsolutePath().startsWith(Paths.get(storageBasePath).toAbsolutePath())) {
                logger.warn("Attempt to delete file outside storage directory: {}", filePath);
                return false;
            }

            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                logger.info("Deleted file: {}", filePath);
            }

            return deleted;

        } catch (Exception e) {
            logger.error("Error deleting file {}: {}", filePath, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validar archivo subido
     */
    private ValidationResult validateFile(MultipartFile file) {
        // Verificar que no esté vacío
        if (file.isEmpty()) {
            return ValidationResult.invalid("El archivo está vacío");
        }

        // Verificar tamaño
        if (file.getSize() > MAX_FILE_SIZE) {
            return ValidationResult.invalid("El archivo excede el tamaño máximo permitido (50MB)");
        }

        // Verificar extensión
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            return ValidationResult.invalid("Nombre de archivo inválido");
        }

        String extension = getFileExtension(fileName).toLowerCase();
        boolean validExtension = false;
        for (String allowedExt : ALLOWED_EXTENSIONS) {
            if (extension.equals(allowedExt)) {
                validExtension = true;
                break;
            }
        }

        if (!validExtension) {
            return ValidationResult.invalid("Solo se permiten archivos PDF");
        }

        // Verificar tipo MIME
        String contentType = file.getContentType();
        if (!"application/pdf".equals(contentType)) {
            return ValidationResult.invalid("El archivo debe ser un PDF válido");
        }

        return ValidationResult.valid();
    }

    /**
     * Obtener extensión del archivo
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }

    /**
     * Generar nombre único para el archivo
     */
    private String generateUniqueFileName(String originalFileName, String extension) {
        String baseName = originalFileName;
        if (baseName.endsWith(extension)) {
            baseName = baseName.substring(0, baseName.length() - extension.length());
        }

        // Limpiar caracteres especiales
        baseName = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Agregar UUID para unicidad
        return baseName + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
    }

    // Clases de resultado
    public static class StorageResult {
        private final boolean success;
        private final String message;
        private final String filePath;
        private final String fileName;
        private final String originalName;
        private final long fileSize;

        private StorageResult(boolean success, String message, String filePath,
                             String fileName, String originalName, long fileSize) {
            this.success = success;
            this.message = message;
            this.filePath = filePath;
            this.fileName = fileName;
            this.originalName = originalName;
            this.fileSize = fileSize;
        }

        public static StorageResult success(String filePath, String fileName, String originalName, long fileSize) {
            return new StorageResult(true, "Archivo almacenado exitosamente",
                                   filePath, fileName, originalName, fileSize);
        }

        public static StorageResult error(String message) {
            return new StorageResult(false, message, null, null, null, 0);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getFilePath() { return filePath; }
        public String getFileName() { return fileName; }
        public String getOriginalName() { return originalName; }
        public long getFileSize() { return fileSize; }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
}