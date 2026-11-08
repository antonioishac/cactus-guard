package br.com.cactus.guard.case_.service;

import br.com.cactus.guard.common.exception.BusinessException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validacao de upload de evidencias: mesmas regras do KYC (PDF/JPG/PNG; content-type + extensao +
 * assinatura; tamanho maximo; nome sanitizado contra path traversal/CRLF).
 */
public final class EvidenceUploadValidator {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "application/pdf", Set.of("pdf"),
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"));

    private EvidenceUploadValidator() {
    }

    /** Valida e devolve o nome sanitizado. */
    public static String validate(String fileName, byte[] content, String contentType, long maxBytes) {
        if (content == null || content.length == 0) {
            throw new BusinessException("Arquivo vazio");
        }
        if (content.length > maxBytes) {
            throw new BusinessException("Arquivo excede o tamanho maximo de " + maxBytes + " bytes");
        }
        String ct = normalizeType(contentType);
        Set<String> exts = ALLOWED.get(ct);
        if (exts == null) {
            throw new BusinessException("Tipo de arquivo nao permitido (aceitos: PDF, JPG, PNG)");
        }
        String safe = sanitizeFileName(fileName);
        String ext = safe.contains(".") ? safe.substring(safe.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!exts.contains(ext)) {
            throw new BusinessException("Extensao do arquivo incompativel com o tipo informado");
        }
        if (!matchesSignature(ct, content)) {
            throw new BusinessException("Conteudo do arquivo nao corresponde ao tipo informado");
        }
        return safe;
    }

    public static String normalizeType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
    }

    static String sanitizeFileName(String original) {
        String name = original == null ? "" : original;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        name = name.replaceAll("\\.{2,}", ".");
        if (name.length() > 100) {
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
            name = name.substring(0, 100 - ext.length()) + ext;
        }
        if (name.isBlank() || name.startsWith(".")) {
            throw new BusinessException("Nome de arquivo invalido");
        }
        return name;
    }

    private static boolean matchesSignature(String ct, byte[] c) {
        return switch (ct) {
            case "application/pdf" -> c.length > 4 && c[0] == '%' && c[1] == 'P' && c[2] == 'D' && c[3] == 'F' && c[4] == '-';
            case "image/jpeg" -> c.length > 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF;
            case "image/png" -> c.length > 7 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G';
            default -> false;
        };
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
