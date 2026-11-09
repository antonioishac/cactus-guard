package br.com.cactus.guard.securityevent.dto;

import jakarta.servlet.http.HttpServletRequest;

/** Origem da requisicao de login: IP (real atras de proxy confiavel, ver ClientIpResolver) e User-Agent resumido (200 chars, sem caracteres de controle). */
public record ClientInfo(String ip, String userAgent) {

    public static final ClientInfo UNKNOWN = new ClientInfo(null, null);

    public static ClientInfo from(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return new ClientInfo(br.com.cactus.guard.config.ClientIpFilter.ipOf(request), summarize(ua));
    }

    static String summarize(String ua) {
        if (ua == null) {
            return null;
        }
        String clean = ua.replaceAll("\\p{Cntrl}", " ").trim();
        return clean.length() > 200 ? clean.substring(0, 200) : clean;
    }
}
