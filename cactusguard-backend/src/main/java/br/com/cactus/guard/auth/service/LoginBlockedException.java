package br.com.cactus.guard.auth.service;

import org.springframework.security.core.AuthenticationException;

/**
 * Bloqueio temporario de login (usuario ou IP). Mensagem generica e identica para usuarios existentes e inexistentes:
 * nao revela se a conta existe. Mapeada para HTTP 429 pelo GlobalExceptionHandler.
 */
public class LoginBlockedException extends AuthenticationException {

    public static final String MESSAGE = "Muitas tentativas de login. Tente novamente mais tarde.";

    public LoginBlockedException() {
        super(MESSAGE);
    }
}
