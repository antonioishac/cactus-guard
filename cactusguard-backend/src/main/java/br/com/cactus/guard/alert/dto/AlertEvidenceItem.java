package br.com.cactus.guard.alert.dto;

public record AlertEvidenceItem(String parameter, String label, Object threshold, Object observed) {
}
