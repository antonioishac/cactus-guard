package br.com.cactus.guard.party.service;

import br.com.cactus.guard.audit.service.DataAccessService;
import java.util.UUID;

import br.com.cactus.guard.party.dto.PartyRequest;
import br.com.cactus.guard.party.dto.PartyResponse;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PartyService {

    private final PartyRepository partyRepository;
    private final DataAccessService dataAccessService;

    @Transactional
    public PartyResponse create(PartyRequest request) {
        String hash = hashDocument(request.getDocument());
        if (partyRepository.existsByDocumentHash(hash)) {
            throw new br.com.cactus.guard.common.exception.BusinessException("Documento já cadastrado");
        }

        Party party = Party.builder()
                .type(request.getType())
                .document(request.getDocument())
                .documentHash(hash)
                .name(request.getName())
                .tradeName(request.getTradeName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .riskLevel("LOW")
                .status("ACTIVE")
                .build();

        party = partyRepository.save(party);
        return toResponse(party);
    }

    private String hashDocument(String document) {
        return br.com.cactus.guard.common.util.DocumentHash.of(document);
    }

    /**
     * Busca por CPF/CNPJ via {@code document_hash} (o documento é criptografado com IV aleatório
     * e não suporta comparação por igualdade).
     */
    @Transactional(readOnly = true)
    public java.util.Optional<PartyResponse> findByDocument(String document) {
        return partyRepository.findByDocumentHash(hashDocument(document))
                .map(party -> {
                    dataAccessService.log("PARTY", party.getId(), "document", "CONSULTA", null);
                    return toResponse(party);
                });
    }

    @Transactional(readOnly = true)
    public PartyResponse findById(UUID id) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new br.com.cactus.guard.common.exception.BusinessException("Party not found: " + id));
        dataAccessService.log("PARTY", id, "document,email,phone", "CONSULTA", null);
        return toResponse(party);
    }

    @Transactional(readOnly = true)
    public Page<PartyResponse> findAll(Pageable pageable) {
        return partyRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PartyResponse> search(String query, Pageable pageable) {
        return partyRepository.searchFullText(query, pageable).map(this::toResponse);
    }

    @Transactional
    public PartyResponse update(UUID id, PartyRequest request) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new br.com.cactus.guard.common.exception.BusinessException("Party not found: " + id));

        if (request.getDocument() != null && !request.getDocument().equals(party.getDocument())) {
            party.setDocument(request.getDocument());
            party.setDocumentHash(hashDocument(request.getDocument()));
        }
        party.setName(request.getName());
        party.setTradeName(request.getTradeName());
        party.setEmail(request.getEmail());
        party.setPhone(request.getPhone());
        if (request.getBirthDate() != null) party.setBirthDate(request.getBirthDate());
        if (request.getNationality() != null) party.setNationality(request.getNationality());
        if (request.getOccupation() != null) party.setOccupation(request.getOccupation());

        party = partyRepository.save(party);
        return toResponse(party);
    }

    @Transactional
    public void delete(UUID id) {
        if (!partyRepository.existsById(id)) {
            throw new IllegalArgumentException("Party not found: " + id);
        }
        partyRepository.deleteById(id);
    }

    private PartyResponse toResponse(Party party) {
        return PartyResponse.from(party, true);
    }
}
