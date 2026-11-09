package br.com.cactus.guard.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * IP real do cliente atras de proxy/balanceador, SEM confiar em quem nao e proxy confiavel.
 * <ul>
 *   <li>Se o par TCP ({@code getRemoteAddr}) NAO esta em {@code cactus.network.trusted-proxies}
 *       ({@code CACTUS_TRUSTED_PROXIES}, lista de IPs/CIDRs separados por virgula), {@code X-Forwarded-For} e
 *       ignorado por completo: um cliente nao consegue forjar o proprio IP para fugir (ou para incriminar outro) do
 *       limitador e do bloqueio de login.</li>
 *   <li>Se o par e confiavel, percorre {@code X-Forwarded-For} da DIREITA para a ESQUERDA descartando enderecos de
 *       proxies confiaveis; o primeiro que nao e confiavel e o cliente. Entradas invalidas encerram a busca e
 *       valem o ultimo endereco valido ja visto (nunca texto livre).</li>
 *   <li>Lista vazia (padrao) = nenhum proxy confiavel: usa sempre o IP do socket.</li>
 * </ul>
 * Por isso o Spring/Tomcat fica com {@code server.forward-headers-strategy=none}: nenhuma outra camada reescreve o
 * endereco por conta propria.
 */
@Slf4j
@Component
public class ClientIpResolver {

    public static final String ATTRIBUTE = "cactus.clientIp";
    private static final String XFF = "X-Forwarded-For";

    private record Cidr(byte[] network, int prefix) {
        boolean contains(byte[] addr) {
            if (addr.length != network.length) {
                return false;
            }
            int fullBytes = prefix / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (addr[i] != network[i]) {
                    return false;
                }
            }
            int rest = prefix % 8;
            if (rest == 0) {
                return true;
            }
            int mask = 0xFF << (8 - rest);
            return (addr[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }

    private final List<Cidr> trusted;

    public ClientIpResolver(@Value("${cactus.network.trusted-proxies:}") String trustedProxies) {
        this.trusted = parse(trustedProxies);
        if (!trusted.isEmpty()) {
            log.info("Proxies confiaveis para X-Forwarded-For: {} entrada(s)", trusted.size());
        }
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (trusted.isEmpty() || !isTrusted(peer)) {
            return peer;
        }
        String header = request.getHeader(XFF);
        if (header == null || header.isBlank()) {
            return peer;
        }
        String[] hops = header.split(",");
        String candidate = peer;
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            byte[] bytes = toBytes(hop);
            if (bytes == null) {
                return candidate; // lixo no cabecalho: nao confia no que vem antes
            }
            candidate = normalize(hop);
            if (!isTrusted(candidate)) {
                return candidate;
            }
        }
        return candidate;
    }

    boolean isTrusted(String ip) {
        byte[] bytes = toBytes(ip);
        if (bytes == null) {
            return false;
        }
        for (Cidr c : trusted) {
            if (c.contains(bytes)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String ip) {
        byte[] b = toBytes(ip);
        try {
            return b == null ? ip : InetAddress.getByAddress(b).getHostAddress();
        } catch (UnknownHostException e) {
            return ip;
        }
    }

    /** So literais IPv4/IPv6 (sem DNS). Devolve 4 ou 16 bytes; IPv4 mapeado em IPv6 vira IPv4. */
    static byte[] toBytes(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45) {
            return null;
        }
        String s = ip.trim();
        boolean v4 = s.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        boolean v6 = s.indexOf(':') >= 0 && s.matches("[0-9a-fA-F:.%]+");
        if (!v4 && !v6) {
            return null; // nome de host/texto livre nunca passa por resolucao de DNS
        }
        try {
            byte[] b = InetAddress.getByName(s.toLowerCase(Locale.ROOT)).getAddress();
            return b;
        } catch (UnknownHostException | RuntimeException e) {
            return null;
        }
    }

    static List<Cidr> parse(String csv) {
        List<Cidr> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String item : csv.split(",")) {
            String s = item.trim();
            if (s.isEmpty()) {
                continue;
            }
            String[] parts = s.split("/");
            byte[] net = toBytes(parts[0]);
            if (net == null || parts.length > 2) {
                throw new IllegalStateException("CACTUS_TRUSTED_PROXIES invalido: '" + s + "' (use IP ou CIDR)");
            }
            int max = net.length * 8;
            int prefix = max;
            if (parts.length == 2) {
                try {
                    prefix = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("CACTUS_TRUSTED_PROXIES invalido: prefixo de '" + s + "'");
                }
                if (prefix < 0 || prefix > max) {
                    throw new IllegalStateException("CACTUS_TRUSTED_PROXIES invalido: prefixo de '" + s + "'");
                }
            }
            out.add(new Cidr(net, prefix));
        }
        return out;
    }
}
