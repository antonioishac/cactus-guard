package br.com.cactus.guard.common.exception;

/** Recurso inexistente (HTTP 404, code {@code NOT_FOUND}). Subclasse de {@link BusinessException} por compatibilidade. */
public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
