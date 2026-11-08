package br.com.cactus.guard.common.exception;

/**
 * Violacao de regra de negocio (HTTP 422, code {@code BUSINESS_RULE}). O codigo pode ser especializado
 * para que o cliente trate o caso sem depender do texto da mensagem.
 */
public class BusinessException extends RuntimeException {

    public static final String DEFAULT_CODE = "BUSINESS_RULE";

    private final String code;

    public BusinessException(String message) {
        this(DEFAULT_CODE, message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
