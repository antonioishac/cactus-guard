package br.com.cactus.guard.party.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PepResponse(
        UUID partyId,
        boolean pep,
        String pepStatus,
        String category,
        String role,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate expiryDate,
        boolean approved,
        boolean eddRequired,
        String riskLevel,
        Instant approvedAt,
        String approvedByName) {
}
