package com.mobydigital.chatbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String category; // "RRHH", "TERMS_CONDITIONS", "POLICIES", "ONBOARDING", etc.

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private LocalDateTime uploadDate;

    @Column(nullable = false)
    private LocalDateTime lastModified;

    @Column(nullable = false)
    private String uploadedBy; // email del usuario que subió el documento

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingStatus status;

    @Column(nullable = false)
    private String fileHash; // para detectar duplicados

    @Column(nullable = false)
    private Long fileSize; // en bytes

    @Column(nullable = false)
    private String filePath; // ruta donde se almacena el archivo

    @Column(length = 500)
    private String mimeType;

    public enum ProcessingStatus {
        UPLOADED,      // Subido pero no procesado
        PROCESSING,    // Siendo procesado (extracción de texto, embeddings)
        COMPLETED,     // Procesado exitosamente
        FAILED,        // Error en el procesamiento
        INACTIVE       // Desactivado temporalmente
    }

    @PrePersist
    protected void onCreate() {
        uploadDate = LocalDateTime.now();
        lastModified = LocalDateTime.now();
        if (status == null) {
            status = ProcessingStatus.UPLOADED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastModified = LocalDateTime.now();
    }
}