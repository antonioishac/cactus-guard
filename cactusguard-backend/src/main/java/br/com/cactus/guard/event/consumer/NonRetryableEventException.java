package br.com.cactus.guard.event.consumer;

/**
 * Erro nao recuperavel de um evento (payload invalido, tenant ausente/invalido, agregado inexistente no tenant):
 * retentar nao adianta, a mensagem vai direto a DLQ. A mensagem NUNCA deve conter trecho do payload recebido.
 */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
