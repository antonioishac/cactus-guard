package br.com.cactus.guard.common.validation;

import br.com.cactus.guard.common.exception.BusinessException;

/** Regra unica de justificativa obrigatoria (decisoes de case, falso positivo, regras, risco). */
public final class Justifications {

    public static final int MIN_CHARS = 20;

    private Justifications() {
    }

    public static boolean isValid(String text) {
        return text != null && text.replaceAll("\s+", "").length() >= MIN_CHARS;
    }

    public static String require(String text, String what) {
        if (!isValid(text)) {
            throw new BusinessException("Justificativa obrigatoria para " + what
                    + " (minimo de " + MIN_CHARS + " caracteres nao brancos)");
        }
        return text.trim();
    }
}
