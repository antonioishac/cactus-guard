package br.com.cactus.guard.party.dto;

import br.com.cactus.guard.common.util.DataMasker;
import br.com.cactus.guard.party.entity.Party;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PartyResponse {

    private UUID id;
    private String type;
    private String document;
    private String name;
    private String tradeName;
    private String email;
    private String phone;
    private String riskLevel;
    private java.time.LocalDate birthDate;
    private String nationality;
    private String occupation;
    private String status;
    private Instant createdAt;

    public static PartyResponse from(Party party, boolean maskSensitive) {
        return PartyResponse.builder()
                .id(party.getId())
                .type(party.getType())
                .document(maskSensitive ? DataMasker.maskDocument(party.getDocument()) : party.getDocument())
                .name(party.getName())
                .tradeName(party.getTradeName())
                .email(maskSensitive ? DataMasker.maskEmail(party.getEmail()) : party.getEmail())
                .phone(maskSensitive ? DataMasker.maskPhone(party.getPhone()) : party.getPhone())
                .riskLevel(party.getRiskLevel())
                .birthDate(party.getBirthDate())
                .nationality(party.getNationality())
                .occupation(party.getOccupation())
                .status(party.getStatus())
                .createdAt(party.getCreatedAt())
                .build();
    }
}
