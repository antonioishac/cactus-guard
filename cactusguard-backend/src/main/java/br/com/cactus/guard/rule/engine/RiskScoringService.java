package br.com.cactus.guard.rule.engine;

import br.com.cactus.guard.event.dto.DomainEventType;
import br.com.cactus.guard.event.service.DomainEventPublisher;
import br.com.cactus.guard.event.service.EventPayloads;
import br.com.cactus.guard.common.audit.AuditPayload;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.party.entity.BeneficialOwner;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.BeneficialOwnerRepository;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.risk.service.RiskHistoryService;
import br.com.cactus.guard.screening.entity.HighRiskJurisdiction;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import br.com.cactus.guard.screening.repository.HighRiskJurisdictionRepository;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Score de risco multifator (Circular BCB 3.978/2020, abordagem baseada em risco).
 * <p>
 * Pontos por fator somam ate 100; alguns fatores impoem piso de nivel (PEP/UBO PEP = HIGH,
 * match pendente = MEDIUM, ex-PEP = MEDIUM) e sancao confirmada fixa CRITICAL/100.
 * Faixas: &lt;40 LOW, &lt;70 MEDIUM, &lt;90 HIGH, &gt;=90 CRITICAL (os valores representativos
 * 25/50/75/100 de {@link #scoreForLevel} permanecem dentro de cada faixa).
 * Revisao periodica: LOW 12 meses, MEDIUM 6, HIGH 3, CRITICAL 1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskScoringService {

    static final Set<String> IRREGULAR_RECEITA = Set.of(
            "SUSPENSA", "CANCELADA", "NULA", "TITULAR_FALECIDO", "INAPTA", "BAIXADA");
    private static final int BATCH_SIZE = 500;

    private final PartyRepository partyRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final BeneficialOwnerRepository beneficialOwnerRepository;
    private final TransactionRepository transactionRepository;
    private final HighRiskJurisdictionRepository jurisdictionRepository;
    private final CaseRepository caseRepository;
    private final RiskHistoryService riskHistoryService;
    private final AuditService auditService;
    private final DomainEventPublisher events;

    /** CNAEs (prefixo, so digitos) de atividades de risco; configuravel - ajustar a politica da instituicao. */
    @Value("${cactus.risk.high-risk-cnaes:9200,6499,6612,6821,4511,4789,6911,9492}")
    private String highRiskCnaesCsv = "9200,6499,6612,6821,4511,4789,6911,9492";

    /** Volume em 30 dias (BRL) a partir do qual PJ com menos de 12 meses e considerada de volume alto. */
    @Value("${cactus.risk.young-company-volume:100000}")
    private BigDecimal youngCompanyVolume = new BigDecimal("100000");

    /** Fatos coletados sobre a parte (separados da regra de pontuacao para testar sem banco). */
    public record RiskInputs(List<ScreeningResult> screening,
                             boolean uboPep,
                             BigDecimal volume30d,
                             boolean highRiskResidence,
                             boolean highRiskCounterparty,
                             long communicatedCases,
                             List<String> riskCnaes,
                             BigDecimal youngCompanyVolume) {
    }

    /** Resultado da avaliacao. */
    public record RiskAssessment(BigDecimal score, String level, List<String> factors, boolean eddRequired) {
    }

    // ------------------------------------------------------------------ batch

    @Transactional
    public void recalculateAllRiskScores() {
        int total = 0;
        int page = 0;
        Page<Party> parties;
        do {
            parties = partyRepository.findAll(PageRequest.of(page++, BATCH_SIZE));
            List<Party> content = parties.getContent();
            Map<UUID, RiskInputs> inputs = loadInputs(content);
            for (Party party : content) {
                apply(party, compute(party, inputs.get(party.getId()), LocalDate.now(BusinessCalendar.BRAZIL)),
                        "Recálculo automático");
            }
            total += content.size();
        } while (parties.hasNext());
        log.info("Risk scores recalculated for {} parties", total);
    }

    /** Avalia (sem persistir). */
    @Transactional(readOnly = true)
    public RiskAssessment assess(Party party) {
        RiskInputs in = loadInputs(List.of(party)).get(party.getId());
        return compute(party, in, LocalDate.now(BusinessCalendar.BRAZIL));
    }

    /** Compatibilidade: apenas o nivel. */
    @Transactional(readOnly = true)
    public String calculateRisk(Party party) {
        return assess(party).level();
    }

    /** Avalia e persiste (score, nivel, data, proxima revisao, EDD, historico, auditoria). */
    @Transactional
    public RiskAssessment assessAndApply(Party party, String trigger) {
        RiskAssessment a = compute(party, loadInputs(List.of(party)).get(party.getId()),
                LocalDate.now(BusinessCalendar.BRAZIL));
        apply(party, a, trigger);
        return a;
    }

    // ------------------------------------------------------------------ persistencia

    private void apply(Party party, RiskAssessment a, String trigger) {
        String previousLevel = party.getRiskLevel();
        BigDecimal previousScore = party.getRiskScore();
        boolean changed = !a.level().equals(previousLevel)
                || previousScore == null || previousScore.compareTo(a.score()) != 0;

        party.setRiskLevel(a.level());
        party.setRiskScore(a.score());
        party.setRiskCalculatedAt(Instant.now());
        party.setNextReviewDate(nextReviewDate(a.level(), LocalDate.now(BusinessCalendar.BRAZIL)));
        // EDD e "pegajosa": so um tratamento humano deve encerrar a diligencia reforcada
        party.setEddRequired(party.isEddRequired() || a.eddRequired());
        partyRepository.save(party);

        if (changed) {
            String reason = trigger + ": " + (a.factors().isEmpty() ? "sem fatores de risco" : String.join("; ", a.factors()));
            if (reason.length() > 255) {
                reason = reason.substring(0, 255);
            }
            riskHistoryService.record(party, previousScore, a.score(), previousLevel, a.level(), reason, null);
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("riskLevel", previousLevel);
            before.put("riskScore", previousScore);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("riskLevel", a.level());
            after.put("riskScore", a.score());
            after.put("factors", a.factors());
            after.put("trigger", trigger);
            after.put("eddRequired", party.isEddRequired());
            AuditPayload.log(auditService, "RISK_RECALCULATED", "PARTY", party.getId(), before, after);
            if (!a.level().equals(previousLevel)) {
                events.publish(DomainEventType.PARTY_RISK_CHANGED, party.getId(), EventPayloads.partyRiskChanged(
                        party.getId(), previousLevel, a.level(), previousScore, a.score(), trigger));
            }
        }
    }

    public static LocalDate nextReviewDate(String level, LocalDate from) {
        return switch (level) {
            case "CRITICAL" -> from.plusMonths(1);
            case "HIGH" -> from.plusMonths(3);
            case "MEDIUM" -> from.plusMonths(6);
            default -> from.plusMonths(12);
        };
    }

    // ------------------------------------------------------------------ coleta (consultas em lote)

    private Map<UUID, RiskInputs> loadInputs(Collection<Party> parties) {
        Map<UUID, RiskInputs> out = new HashMap<>();
        if (parties.isEmpty()) return out;
        List<UUID> ids = parties.stream().map(Party::getId).toList();
        LocalDate today = LocalDate.now(BusinessCalendar.BRAZIL);
        Instant since30 = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant since12m = Instant.now().minus(365, ChronoUnit.DAYS);

        Map<UUID, List<ScreeningResult>> screening = screeningResultRepository.findByPartyIdIn(ids).stream()
                .collect(Collectors.groupingBy(r -> r.getParty().getId()));
        Set<UUID> uboPep = beneficialOwnerRepository.findByPartyIdIn(ids).stream()
                .filter(BeneficialOwner::isPep).map(b -> b.getParty().getId()).collect(Collectors.toSet());
        Map<UUID, BigDecimal> volume = new HashMap<>();
        for (Object[] row : transactionRepository.volumeByPartyIn(ids, since30)) {
            volume.put((UUID) row[0], new BigDecimal(row[1].toString()));
        }
        Set<String> countries = jurisdictionRepository.findAll().stream()
                .filter(j -> isValid(j, today)).map(j -> j.getCountryCode().toUpperCase()).collect(Collectors.toSet());
        Set<UUID> hrjCounterparty = countries.isEmpty() ? Set.of()
                : new HashSet<>(transactionRepository.findPartyIdsWithCountriesSince(ids, countries, since12m));
        Map<UUID, Long> cases = caseRepository.findByPartyIdInAndDecision(ids, "COMMUNICATE").stream()
                .collect(Collectors.groupingBy(c -> c.getParty().getId(), Collectors.counting()));
        List<String> cnaes = Arrays.stream(highRiskCnaesCsv.split(",")).map(s -> s.replaceAll("\\D", ""))
                .filter(s -> !s.isEmpty()).toList();

        for (Party p : parties) {
            boolean residence = inCountries(countries, p.getCountryOfResidence())
                    || inCountries(countries, p.getTaxResidenceCountry());
            out.put(p.getId(), new RiskInputs(
                    screening.getOrDefault(p.getId(), List.of()),
                    uboPep.contains(p.getId()),
                    volume.getOrDefault(p.getId(), BigDecimal.ZERO),
                    residence,
                    hrjCounterparty.contains(p.getId()),
                    cases.getOrDefault(p.getId(), 0L),
                    cnaes,
                    youngCompanyVolume));
        }
        return out;
    }

    private static boolean isValid(HighRiskJurisdiction j, LocalDate d) {
        return !j.getValidFrom().isAfter(d) && (j.getValidTo() == null || !j.getValidTo().isBefore(d));
    }

    private static boolean inCountries(Set<String> countries, String c) {
        return c != null && countries.contains(c.toUpperCase());
    }

    // ------------------------------------------------------------------ pontuacao (pura)

    static RiskAssessment compute(Party party, RiskInputs in, LocalDate today) {
        List<String> factors = new ArrayList<>();
        int points = 0;
        int floor = 0;
        boolean edd = false;

        // Sancao confirmada: CRITICAL imediato
        boolean sanction = in.screening().stream().anyMatch(r -> "CONFIRMED".equals(r.getStatus()) && isSanction(r));
        if (sanction) {
            factors.add("SANCAO_CONFIRMADA");
            return new RiskAssessment(scoreForLevel("CRITICAL"), "CRITICAL", factors, true);
        }

        boolean pepActive = party.isPep() && !"EXPIRED".equals(party.getPepStatus()) && !"NONE".equals(party.getPepStatus());
        boolean pepScreening = in.screening().stream().anyMatch(r -> "CONFIRMED".equals(r.getStatus())
                && r.getListCode() != null && r.getListCode().contains("PEP"));
        if (pepActive || pepScreening) {
            points += 45;
            floor = Math.max(floor, 75);
            edd = true;
            factors.add("PEP");
        } else if ("EXPIRED".equals(party.getPepStatus())) {
            points += 20;
            floor = Math.max(floor, 50);
            factors.add("EX_PEP");
        }
        if (in.uboPep()) {
            points += 30;
            floor = Math.max(floor, 75);
            edd = true;
            factors.add("UBO_PEP");
        }
        if (in.screening().stream().anyMatch(r -> "CONFIRMED".equals(r.getStatus()) && isAdverseMedia(r))) {
            points += 30;
            factors.add("MIDIA_ADVERSA");
        }
        if (in.screening().stream().anyMatch(r -> "PENDING".equals(r.getStatus()))) {
            floor = Math.max(floor, 50);
            factors.add("MATCH_PENDENTE");
        }
        if (in.highRiskResidence()) {
            points += 40;
            factors.add("JURISDICAO_ALTO_RISCO_RESIDENCIA");
        }
        if (in.highRiskCounterparty()) {
            points += 25;
            factors.add("JURISDICAO_ALTO_RISCO_CONTRAPARTE");
        }
        boolean pj = "PJ".equals(party.getType());
        if (pj && cnaeRisky(party, in.riskCnaes())) {
            points += 25;
            factors.add("ATIVIDADE_RISCO");
        }
        if (pj && !party.isPubliclyTraded()) {
            if (Boolean.FALSE.equals(party.getUboIdentified())) {
                points += 35;
                factors.add("UBO_NAO_IDENTIFICADO");
            } else if (party.getUboIdentified() == null) {
                points += 10;
                factors.add("UBO_NAO_AVALIADO");
            }
        }
        if (party.getReceitaStatus() != null && IRREGULAR_RECEITA.contains(party.getReceitaStatus())) {
            points += 40;
            factors.add("RECEITA_" + party.getReceitaStatus());
        }
        if (pj && party.getIncorporationDate() != null
                && party.getIncorporationDate().isAfter(today.minusMonths(12))
                && in.volume30d().compareTo(in.youngCompanyVolume()) >= 0) {
            points += 20;
            factors.add("PJ_RECENTE_VOLUME_ALTO");
        }
        BigDecimal reference = pj
                ? (party.getAnnualRevenue() != null ? party.getAnnualRevenue().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP) : null)
                : party.getDeclaredMonthlyIncome();
        if (reference != null && reference.signum() > 0) {
            BigDecimal limit = reference.multiply(pj ? new BigDecimal("1.5") : new BigDecimal("3"));
            if (in.volume30d().compareTo(limit) > 0) {
                points += 30;
                factors.add("VOLUME_INCOMPATIVEL");
            }
        }
        if (in.communicatedCases() > 0) {
            points += (int) Math.min(50, 30 + 10 * (in.communicatedCases() - 1));
            factors.add("ALERTAS_CONFIRMADOS(" + in.communicatedCases() + ")");
        }

        // 25 = risco inerente minimo (valor representativo de LOW, compativel com scoreForLevel)
        int raw = Math.min(100, Math.max(25, Math.max(points, floor)));
        String level = levelFor(raw);
        edd = edd || "HIGH".equals(level) || "CRITICAL".equals(level);
        return new RiskAssessment(BigDecimal.valueOf(raw).setScale(2), level, factors, edd);
    }

    static String levelFor(int score) {
        if (score >= 90) return "CRITICAL";
        if (score >= 70) return "HIGH";
        if (score >= 40) return "MEDIUM";
        return "LOW";
    }

    private static boolean isSanction(ScreeningResult r) {
        String code = r.getListCode() != null ? r.getListCode() : "";
        String type = r.getListType() != null ? r.getListType() : "";
        return code.contains("SANCTION") || code.contains("OFAC") || code.contains("UN_") || code.contains("CSNU")
                || "SANCTION".equals(type) || "CSNU".equals(type);
    }

    private static boolean isAdverseMedia(ScreeningResult r) {
        return (r.getListCode() != null && r.getListCode().contains("MEDIA"))
                || "ADVERSE_MEDIA".equals(r.getListType());
    }

    private static boolean cnaeRisky(Party party, List<String> riskCnaes) {
        List<String> all = new ArrayList<>();
        if (party.getCnaePrimary() != null) all.add(party.getCnaePrimary());
        if (party.getCnaesSecondary() != null) all.addAll(party.getCnaesSecondary());
        return all.stream().map(s -> s.replaceAll("\\D", "")).anyMatch(c -> riskCnaes.stream().anyMatch(c::startsWith));
    }

    /** Score numerico (0-100) representativo de cada nivel de risco. */
    static BigDecimal scoreForLevel(String level) {
        return switch (level) {
            case "CRITICAL" -> BigDecimal.valueOf(100).setScale(2);
            case "HIGH" -> BigDecimal.valueOf(75).setScale(2);
            case "MEDIUM" -> BigDecimal.valueOf(50).setScale(2);
            default -> BigDecimal.valueOf(25).setScale(2);
        };
    }
}
