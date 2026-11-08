package br.com.cactus.guard.alert.dto;

public record RuleSummary(String code, String name, Integer version, String category, String severity,
                          String carta4001Reference) {
}
