package com.mobydigital.chatbot.dto;

import com.mobydigital.chatbot.model.Document;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDTO {

    private Long id;
    private String fileName;
    private String originalName;
    private String category;
    private String description;
    private LocalDateTime uploadDate;
    private LocalDateTime lastModified;
    private String uploadedBy;
    private String status;
    private Long fileSize;
    private String mimeType;

    public static DocumentDTO fromEntity(Document document) {
        return new DocumentDTO(
            document.getId(),
            document.getFileName(),
            document.getOriginalName(),
            document.getCategory(),
            document.getDescription(),
            document.getUploadDate(),
            document.getLastModified(),
            document.getUploadedBy(),
            document.getStatus().name(),
            document.getFileSize(),
            document.getMimeType()
        );
    }
}