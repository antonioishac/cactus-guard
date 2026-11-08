package br.com.cactus.guard.integration.push;

/** Porta de envio de push. Implementacoes: {@link NoopPushSender} (padrao) e {@link FcmPushSender}. */
public interface PushSenderPort {

    PushResult send(String deviceToken, PushMessage message);

    /** Mascara o token para logs (nunca em claro). */
    static String mask(String token) {
        if (token == null || token.length() <= 12) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
