package com.mobydigital.chatbot.repository;

import com.mobydigital.chatbot.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // Buscar documentos por categoría
    List<Document> findByCategory(String category);

    // Buscar documentos por estado de procesamiento
    List<Document> findByStatus(Document.ProcessingStatus status);

    // Buscar documentos por usuario que los subió
    List<Document> findByUploadedBy(String uploadedBy);

    // Buscar por hash de archivo para detectar duplicados
    Optional<Document> findByFileHash(String fileHash);

    // Buscar documentos procesados exitosamente
    List<Document> findByStatusOrderByUploadDateDesc(Document.ProcessingStatus status);

    // Buscar por nombre de archivo
    Optional<Document> findByFileName(String fileName);

    // Buscar documentos por rango de fechas
    @Query("SELECT d FROM Document d WHERE d.uploadDate BETWEEN :startDate AND :endDate ORDER BY d.uploadDate DESC")
    List<Document> findByUploadDateBetween(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    // Buscar documentos que contengan texto en nombre o descripción
    @Query("SELECT d FROM Document d WHERE LOWER(d.originalName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(d.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Document> findBySearchTerm(@Param("searchTerm") String searchTerm);

    // Contar documentos por categoría
    @Query("SELECT d.category, COUNT(d) FROM Document d GROUP BY d.category")
    List<Object[]> countByCategory();

    // Obtener documentos recientes (útil para dashboard)
    @Query("SELECT d FROM Document d WHERE d.status = 'COMPLETED' ORDER BY d.uploadDate DESC")
    List<Document> findRecentProcessedDocuments();
}