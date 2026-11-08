package br.com.cactus.guard.common.util;

public final class DataMasker {

    private DataMasker() {}

    public static String maskDocument(String document) {
        if (document == null || document.length() < 4) return "***";
        int len = document.length();
        return "*".repeat(len - 4) + document.substring(len - 4);
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        String local = parts[0];
        String masked = local.length() > 2 ? local.charAt(0) + "***" + local.charAt(local.length() - 1) : "***";
        return masked + "@" + parts[1];
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }

    /** Nome (ex.: nome da mae): primeira letra de cada parte, o restante oculto. */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) return "***";
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\s+")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(part.charAt(0)).append("***");
        }
        return sb.toString();
    }
}
