package br.com.cactus.guard.party.service;

import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.audit.service.DataAccessService;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.exception.NotFoundException;
import br.com.cactus.guard.common.security.CurrentUserService;
import br.com.cactus.guard.common.util.DocumentHash;
import br.com.cactus.guard.party.dto.PartyFilter;
import br.com.cactus.guard.party.dto.PartyRequest;
import br.com.cactus.guard.party.dto.PartyResponse;
import br.com.cactus.guard.party.dto.RevealRequest;
import br.com.cactus.guard.party.dto.RevealResponse;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.engine.RiskScoringService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartyService {

    /** Campos de PII que podem ser revelados (POST /parties/{id}/reveal). */
    public static final Set<String> REVEALABLE_FIELDS = Set.of("document", "email", "phone", "motherName");
    private static final int FULL_TEXT_MAX_IDS = 1000;

    private final PartyRepository partyRepository;
    private final DataAccessService dataAccessService;
    private final AuditService auditService;
    private final RiskScoringService riskScoring;
    private final CurrentUserService currentUser;

    @Transactional
    public PartyResponse create(PartyRequest request) {
        if (request.getDocument() == null || request.getDocument().isBlank()) {
            throw new IllegalArgumentException("document: documento é obrigatório no cadastro");
        }
        if (isMasked(request.getDocument())) {
            throw new IllegalArgumentException("document: informe o documento completo (valor mascarado)");
        }
        String hash = hashDocument(request.getDocument());
        if (partyRepository.existsByDocumentHash(hash)) {
            throw new BusinessException("Documento já cadastrado");
        }

        Party party = Party.builder()
                .type(request.getType())
                .document(request.getDocument())
                .documentHash(hash)
                .name(request.getName())
                .tradeName(request.getTradeName())
                .email(blankToNull(request.getEmail()))
                .phone(blankToNull(request.getPhone()))
                .riskLevel("LOW")
                .status("ACTIVE")
                .build();
        applyOptional(party, request);
        if (party.getPartyRole() == null) {
            party.setPartyRole("CUSTOMER");
        }

        party = partyRepository.save(party);
        auditService.log("PARTY_CREATED", "PARTY", party.getId(), null,
                Map.of("type", party.getType(), "partyRole", party.getPartyRole()));
        return toResponse(party);
    }

    private String hashDocument(String document) {
        return DocumentHash.of(document);
    }

    /**
     * Busca por CPF/CNPJ via {@code document_hash} (o documento e criptografado com IV aleatorio
     * e nao suporta comparacao por igualdade).
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
                .orElseThrow(() -> new NotFoundException("Party not found: " + id));
        dataAccessService.log("PARTY", id, "document,email,phone", "CONSULTA", null);
        return toResponse(party);
    }

    @Transactional(readOnly = true)
    public Page<PartyResponse> findAll(Pageable pageable) {
        return partyRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Listagem filtrada. {@code document} usa igualdade de HMAC; {@code q} usa full-text (tsvector) e, se
     * parecer CPF/CNPJ, tambem o hash do documento. Nunca LIKE em campos de PII.
     */
    @Transactional(readOnly = true)
    public Page<PartyResponse> findAll(PartyFilter f, Pageable pageable) {
        List<UUID> qIds = null;
        if (hasText(f.q())) {
            Set<UUID> ids = new LinkedHashSet<>();
            String q = f.q().trim();
            if (q.matches("[0-9A-Za-z./-]{11,18}") && q.chars().anyMatch(Character::isDigit) && !q.contains(" ")) {
                partyRepository.findByDocumentHash(hashDocument(q)).ifPresent(p -> ids.add(p.getId()));
            }
            ids.addAll(partyRepository.searchIdsFullText(q, FULL_TEXT_MAX_IDS));
            if (ids.isEmpty()) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            qIds = new ArrayList<>(ids);
        }
        final List<UUID> idFilter = qIds;
        final String docHash = hasText(f.document()) ? hashDocument(f.document()) : null;

        Specification<Party> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (idFilter != null) ps.add(root.get("id").in(idFilter));
            if (docHash != null) ps.add(cb.equal(root.get("documentHash"), docHash));
            if (hasText(f.type())) ps.add(cb.equal(root.get("type"), f.type().trim().toUpperCase(Locale.ROOT)));
            if (hasText(f.riskLevel())) ps.add(cb.equal(root.get("riskLevel"), f.riskLevel().trim().toUpperCase(Locale.ROOT)));
            if (f.pep() != null) ps.add(cb.equal(root.get("pep"), f.pep()));
            if (hasText(f.sourceType())) ps.add(cb.equal(root.get("sourceType"), f.sourceType().trim().toUpperCase(Locale.ROOT)));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return partyRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PartyResponse> search(String query, Pageable pageable) {
        return partyRepository.searchFullText(query, pageable).map(this::toResponse);
    }

    /**
     * Edicao. PII que contenha {@code *} (valor mascarado devolvido pela API) e ignorada: o atual e
     * mantido. O documento e imutavel: se vier completo e diferente, 422. {@code null} em
     * email/phone/motherName mantem o atual; string vazia limpa o campo.
     */
    @Transactional
    public PartyResponse update(UUID id, PartyRequest request) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Party not found: " + id));

        String doc = request.getDocument();
        if (hasText(doc) && !isMasked(doc)) {
            String current = party.getDocument();
            boolean same = current != null && DocumentHash.of(current).equals(hashDocument(doc));
            if (!same) {
                throw new BusinessException("DOCUMENT_IMMUTABLE",
                        "O documento não pode ser alterado após o cadastro");
            }
        }

        List<String> changed = new ArrayList<>();
        if (!java.util.Objects.equals(party.getName(), request.getName())) changed.add("name");
        party.setName(request.getName());
        if (!java.util.Objects.equals(party.getTradeName(), request.getTradeName())) changed.add("tradeName");
        party.setTradeName(request.getTradeName());

        if (applyPii(request.getEmail(), party.getEmail(), party::setEmail)) changed.add("email");
        if (applyPii(request.getPhone(), party.getPhone(), party::setPhone)) changed.add("phone");
        if (applyPii(request.getMotherName(), party.getMotherName(), party::setMotherName)) changed.add("motherName");

        if (request.getBirthDate() != null) party.setBirthDate(request.getBirthDate());
        if (request.getNationality() != null) party.setNationality(request.getNationality());
        if (request.getOccupation() != null) party.setOccupation(request.getOccupation());
        applyOptional(party, request);

        party = partyRepository.save(party);
        // so NOMES dos campos de PII alterados: nunca valores (LGPD)
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("changedPiiOrKeyFields", changed);
        auditService.log("PARTY_UPDATED", "PARTY", party.getId(), null, after);
        return toResponse(party);
    }

    /** Revela PII em claro: exige motivo, registra DataAccessLog (LGPD) e auditoria (sem o valor). */
    @Transactional
    public RevealResponse reveal(UUID id, RevealRequest request, String ip) {
        Set<String> fields = new LinkedHashSet<>();
        if (hasText(request.field())) fields.add(request.field().trim());
        if (request.fields() != null) request.fields().stream().filter(PartyService::hasText).map(String::trim).forEach(fields::add);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("field: informe ao menos um campo");
        }
        for (String f : fields) {
            if (!REVEALABLE_FIELDS.contains(f)) {
                throw new IllegalArgumentException("field: campo não revelável");
            }
        }
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Party not found: " + id));

        String csv = String.join(",", fields);
        dataAccessService.log("PARTY", id, csv, "REVELACAO: " + request.reason().trim(), ip);
        auditService.log("PII_REVEALED", "PARTY", id, null, Map.of("fields", new ArrayList<>(fields)));
        log.info("PII revelada: party={} campos={} por={}", id, csv,
                currentUser.user().map(u -> u.getUsername()).orElse("?"));

        Map<String, String> values = new LinkedHashMap<>();
        for (String f : fields) {
            values.put(f, switch (f) {
                case "document" -> party.getDocument();
                case "email" -> party.getEmail();
                case "phone" -> party.getPhone();
                default -> party.getMotherName();
            });
        }
        String first = fields.iterator().next();
        return new RevealResponse(first, values.get(first), values);
    }

    /** Recalculo manual do risco de UMA parte (score, nivel, EDD, historico e auditoria no RiskScoringService). */
    @Transactional
    public PartyResponse recalculateRisk(UUID id) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Party not found: " + id));
        String by = currentUser.user().map(u -> u.getUsername()).orElse("sistema");
        riskScoring.assessAndApply(party, "Recálculo manual (" + by + ")");
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

    // ------------------------------------------------------------------ helpers

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean isMasked(String s) {
        return s != null && s.contains("*");
    }

    private static String blankToNull(String s) {
        return hasText(s) ? s : null;
    }

    /** @return true se o valor foi alterado. */
    private static boolean applyPii(String incoming, String current, java.util.function.Consumer<String> setter) {
        if (incoming == null || isMasked(incoming)) {
            return false;
        }
        String next = incoming.isBlank() ? null : incoming;
        if (java.util.Objects.equals(next, current)) {
            return false;
        }
        setter.accept(next);
        return true;
    }

    /** Campos opcionais do catalogo 046: aplica somente os nao nulos. */
    private void applyOptional(Party p, PartyRequest r) {
        if (r.getBirthDate() != null) p.setBirthDate(r.getBirthDate());
        if (r.getNationality() != null) p.setNationality(r.getNationality());
        if (r.getOccupation() != null) p.setOccupation(r.getOccupation());
        if (r.getMotherName() != null && !isMasked(r.getMotherName()) && p.getMotherName() == null) {
            p.setMotherName(blankToNull(r.getMotherName()));
        }
        if (r.getPartyRole() != null) p.setPartyRole(r.getPartyRole());
        if (r.getRelationshipStartDate() != null) p.setRelationshipStartDate(r.getRelationshipStartDate());
        if (r.getCountryOfResidence() != null) p.setCountryOfResidence(r.getCountryOfResidence());
        if (r.getCboCode() != null) p.setCboCode(r.getCboCode());
        if (r.getDeclaredMonthlyIncome() != null) p.setDeclaredMonthlyIncome(r.getDeclaredMonthlyIncome());
        if (r.getDeclaredNetWorth() != null) p.setDeclaredNetWorth(r.getDeclaredNetWorth());
        if (r.getSourceOfFunds() != null) p.setSourceOfFunds(r.getSourceOfFunds());
        if (r.getSourceOfWealth() != null) p.setSourceOfWealth(r.getSourceOfWealth());
        if (r.getPurposeOfRelationship() != null) p.setPurposeOfRelationship(r.getPurposeOfRelationship());
        if (r.getExpectedMonthlyVolume() != null) p.setExpectedMonthlyVolume(r.getExpectedMonthlyVolume());
        if (r.getIncorporationDate() != null) p.setIncorporationDate(r.getIncorporationDate());
        if (r.getCnaePrimary() != null) p.setCnaePrimary(r.getCnaePrimary());
        if (r.getCnaesSecondary() != null) p.setCnaesSecondary(r.getCnaesSecondary());
        if (r.getLegalNatureCode() != null) p.setLegalNatureCode(r.getLegalNatureCode());
        if (r.getAnnualRevenue() != null) p.setAnnualRevenue(r.getAnnualRevenue());
        if (r.getShareCapital() != null) p.setShareCapital(r.getShareCapital());
        if (r.getCompanySize() != null) p.setCompanySize(r.getCompanySize());
        if (r.getNonprofit() != null) p.setNonprofit(r.getNonprofit());
        if (r.getPubliclyTraded() != null) p.setPubliclyTraded(r.getPubliclyTraded());
    }
}
