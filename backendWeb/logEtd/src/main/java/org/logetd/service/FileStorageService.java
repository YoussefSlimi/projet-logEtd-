package org.logetd.service;

import org.logetd.shared.ApiErrorResponse;
import org.logetd.shared.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Stockage local des photos : seul le nom de fichier généré est persisté en base. */
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final Path directory;
    private final long maxFileSize;

    public FileStorageService(@Value("${app.storage.photos-dir}") String photosDir,
                              @Value("${app.storage.max-photo-size-bytes}") long maxFileSize) {
        this.directory = Path.of(photosDir).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw error("PHOTO_EMPTY", "Le fichier photo est vide.", "file", "fichier vide");
        }
        if (file.getSize() > maxFileSize) {
            throw error("PHOTO_TOO_LARGE", "La photo dépasse la taille maximale autorisée.",
                    "file", "5 Mo maximum");
        }
        String extension = extensionOf(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + "." + extension;
        Path target = resolve(fileName);

        try {
            Files.createDirectories(directory);
            Files.copy(file.getInputStream(), target);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_STORAGE_ERROR",
                    "Impossible d'enregistrer la photo.");
        }
        return fileName;
    }

    /** Suppression best-effort utilisée en cas d'échec métier après écriture du fichier. */
    public void delete(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(fileName));
        } catch (IOException ex) {
            // Le fichier orphelin sera nettoyé manuellement ; aucune donnée personnelle n'est loggée.
        }
    }

    private Path resolve(String fileName) {
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) {
            throw error("INVALID_PHOTO_PATH", "Nom de fichier invalide.", "file", "path traversal");
        }
        return target;
    }

    private String extensionOf(String originalFileName) {
        if (originalFileName == null) {
            throw error("INVALID_PHOTO_TYPE", "Type de photo non autorisé.", "file", "extension manquante");
        }
        int dot = originalFileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : originalFileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw error("INVALID_PHOTO_TYPE", "Type de photo non autorisé.", "file",
                    "extensions acceptées : jpg, jpeg, png, webp");
        }
        return extension;
    }

    private ApiException error(String code, String message, String field, String issue) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message,
                List.of(new ApiErrorResponse.FieldIssue(field, issue)));
    }
}
