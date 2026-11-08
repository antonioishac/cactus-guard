package br.com.cactus.guard.siscoaf.dto;

/** Envio ao COAF: justificativa em {@code reason} (opcional; quando informada, minimo de 20 caracteres). */
public record SiscoafSubmitRequest(String reason) {
}
