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

/** Stockage local des preuves de paiement : seul le nom de fichier généré est persisté. */
@Service
public class PaymentProofStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "pdf");

    private final Path directory;
    private final long maxFileSize;

    public PaymentProofStorageService(@Value("${app.storage.payment-proofs-dir}") String proofsDir,
                                      @Value("${app.storage.max-payment-proof-size-bytes}") long maxFileSize) {
        this.directory = Path.of(proofsDir).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw error("PAYMENT_PROOF_REQUIRED", "La preuve de paiement est obligatoire.", "proof");
        }
        if (file.getSize() > maxFileSize) {
            throw error("PAYMENT_PROOF_TOO_LARGE", "La preuve dépasse la taille maximale autorisée.",
                    "5 Mo maximum");
        }
        String extension = extensionOf(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + "." + extension;
        Path target = resolve(fileName);

        try {
            Files.createDirectories(directory);
            Files.copy(file.getInputStream(), target);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT_PROOF_STORAGE_ERROR",
                    "Impossible d'enregistrer la preuve de paiement.");
        }
        return fileName;
    }

    /** Suppression best-effort si la transaction métier échoue après l'écriture du fichier. */
    public void delete(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(fileName));
        } catch (IOException | ApiException ex) {
            // Fichier orphelin nettoyé manuellement ; aucune donnée personnelle n'est loggée.
        }
    }

    private Path resolve(String fileName) {
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) {
            throw error("INVALID_PAYMENT_PROOF_PATH", "Nom de fichier invalide.", "path traversal");
        }
        return target;
    }

    private String extensionOf(String originalFileName) {
        if (originalFileName == null) {
            throw error("INVALID_PAYMENT_PROOF_TYPE", "Type de preuve non autorisé.", "extension manquante");
        }
        int dot = originalFileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : originalFileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw error("INVALID_PAYMENT_PROOF_TYPE", "Type de preuve non autorisé.",
                    "extensions acceptées : jpg, jpeg, png, webp, pdf");
        }
        return extension;
    }

    private ApiException error(String code, String message, String issue) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message,
                List.of(new ApiErrorResponse.FieldIssue("proof", issue)));
    }
}
