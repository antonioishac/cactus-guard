package br.com.cactus.guard.siscoaf.dto;

/** Aprovacao da comunicacao: justificativa em {@code comment} (ou {@code justification}); obrigatoria. */
public record SiscoafApproveRequest(String comment, String justification) {
    public String text() {
        return comment != null && !comment.isBlank() ? comment : justification;
    }
}
