package br.com.cactus.guard.rule.engine;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.rule.engine.RiskScoringService.RiskAssessment;
import br.com.cactus.guard.rule.engine.RiskScoringService.RiskInputs;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Regras de pontuacao do risco, sem banco. */
class RiskScoringComputeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);
    private static final List<String> CNAES = List.of("9200", "6499");

    private static Party.PartyBuilder pf() {
        return Party.builder().type("PF").document("1").name("n").status("ACTIVE");
    }

    private static Party.PartyBuilder pj() {
        return Party.builder().type("PJ").document("1").name("n").status("ACTIVE");
    }

    private static RiskInputs none() {
        return inputs(List.of(), false, "0", false, false, 0);
    }

    private static RiskInputs inputs(List<ScreeningResult> s, boolean uboPep, String volume,
                                     boolean hrjResidence, boolean hrjCounterparty, long cases) {
        return new RiskInputs(s, uboPep, new BigDecimal(volume), hrjResidence, hrjCounterparty, cases,
                CNAES, new BigDecimal("100000"));
    }

    private static ScreeningResult result(String list, String type, String status) {
        return ScreeningResult.builder().listCode(list).listType(type).status(status).build();
    }

    private static RiskAssessment compute(Party p, RiskInputs in) {
        return RiskScoringService.compute(p, in, TODAY);
    }

    @Test
    void cleanPartyIsLowWithInherentScore25() {
        RiskAssessment a = compute(pf().build(), none());
        assertEquals("LOW", a.level());
        assertEquals(0, new BigDecimal("25").compareTo(a.score()));
        assertTrue(a.factors().isEmpty());
        assertFalse(a.eddRequired());
    }

    @Test
    void confirmedSanctionIsCriticalRegardlessOfOtherFactors() {
        for (ScreeningResult r : List.of(result("OFAC", "SANCTION", "CONFIRMED"),
                result("CSNU", "CSNU", "CONFIRMED"), result("UN_SANCTIONS", null, "CONFIRMED"))) {
            RiskAssessment a = compute(pf().build(), inputs(List.of(r), false, "0", false, false, 0));
            assertEquals("CRITICAL", a.level());
            assertEquals(0, new BigDecimal("100").compareTo(a.score()));
            assertTrue(a.eddRequired());
        }
    }

    @Test
    void unconfirmedSanctionDoesNotMakeCritical() {
        RiskAssessment a = compute(pf().build(),
                inputs(List.of(result("OFAC", "SANCTION", "FALSE_POSITIVE")), false, "0", false, false, 0));
        assertEquals("LOW", a.level());
        a = compute(pf().build(), inputs(List.of(result("OFAC", "SANCTION", "PENDING")), false, "0", false, false, 0));
        assertEquals("MEDIUM", a.level());
        assertTrue(a.factors().contains("MATCH_PENDENTE"));
    }

    @Test
    void pepForcesHighAndEdd() {
        RiskAssessment a = compute(pf().pep(true).pepStatus("ACTIVE").build(), none());
        assertEquals("HIGH", a.level());
        assertTrue(a.score().compareTo(new BigDecimal("75")) >= 0);
        assertTrue(a.eddRequired());
        assertTrue(a.factors().contains("PEP"));
    }

    @Test
    void pepFromConfirmedScreeningAndUboPep() {
        assertEquals("HIGH", compute(pf().build(),
                inputs(List.of(result("PEP_BR", "PEP", "CONFIRMED")), false, "0", false, false, 0)).level());
        RiskAssessment ubo = compute(pj().publiclyTraded(true).build(), inputs(List.of(), true, "0", false, false, 0));
        assertEquals("HIGH", ubo.level());
        assertTrue(ubo.factors().contains("UBO_PEP"));
        assertTrue(ubo.eddRequired());
    }

    @Test
    void expiredPepKeepsAtLeastMedium() {
        RiskAssessment a = compute(pf().pep(true).pepStatus("EXPIRED").build(), none());
        assertEquals("MEDIUM", a.level());
        assertTrue(a.factors().contains("EX_PEP"));
    }

    @Test
    void adverseMediaAddsPoints() {
        RiskAssessment a = compute(pf().build(),
                inputs(List.of(result("ADVERSE_MEDIA_X", "ADVERSE_MEDIA", "CONFIRMED")), false, "0", false, false, 0));
        assertTrue(a.factors().contains("MIDIA_ADVERSA"));
        assertEquals("LOW", a.level(), "sozinha nao muda o nivel (30 pontos)");
    }

    @Test
    void highRiskJurisdictionFactors() {
        RiskAssessment residence = compute(pf().build(), inputs(List.of(), false, "0", true, false, 0));
        assertEquals("MEDIUM", residence.level());
        assertTrue(residence.factors().contains("JURISDICAO_ALTO_RISCO_RESIDENCIA"));
        RiskAssessment both = compute(pf().build(), inputs(List.of(), false, "0", true, true, 0));
        assertEquals("MEDIUM", both.level());
        assertTrue(both.factors().contains("JURISDICAO_ALTO_RISCO_CONTRAPARTE"));
        assertEquals(0, new BigDecimal("65").compareTo(both.score()));
    }

    @Test
    void riskyCnaeOnlyForLegalEntities() {
        RiskAssessment pjRisk = compute(pj().cnaePrimary("92.00-3/01").publiclyTraded(true).build(), none());
        assertTrue(pjRisk.factors().contains("ATIVIDADE_RISCO"));
        RiskAssessment secondary = compute(pj().cnaePrimary("47.11-3/02").cnaesSecondary(List.of("6499-9/99"))
                .publiclyTraded(true).build(), none());
        assertTrue(secondary.factors().contains("ATIVIDADE_RISCO"));
        assertFalse(compute(pf().cnaePrimary("9200").build(), none()).factors().contains("ATIVIDADE_RISCO"));
        assertFalse(compute(pj().cnaePrimary("4711").publiclyTraded(true).build(), none()).factors().contains("ATIVIDADE_RISCO"));
    }

    @Test
    void uboNotIdentifiedInLegalEntity() {
        RiskAssessment notIdentified = compute(pj().uboIdentified(false).build(), none());
        assertTrue(notIdentified.factors().contains("UBO_NAO_IDENTIFICADO"));
        assertEquals("LOW", notIdentified.level());
        assertTrue(compute(pj().build(), none()).factors().contains("UBO_NAO_AVALIADO"));
        assertTrue(compute(pj().uboIdentified(true).build(), none()).factors().isEmpty());
        assertTrue(compute(pj().uboIdentified(false).publiclyTraded(true).build(), none()).factors().isEmpty(),
                "companhia aberta dispensa UBO");
        assertTrue(compute(pf().build(), none()).factors().isEmpty());
    }

    @Test
    void irregularReceitaStatusesAreFactors() {
        for (String s : RiskScoringService.IRREGULAR_RECEITA) {
            RiskAssessment a = compute(pf().receitaStatus(s).build(), none());
            assertTrue(a.factors().contains("RECEITA_" + s), s);
            assertEquals("MEDIUM", a.level(), s);
        }
        assertTrue(compute(pf().receitaStatus("REGULAR").build(), none()).factors().isEmpty());
        assertTrue(compute(pf().receitaStatus("PENDENTE").build(), none()).factors().isEmpty());
    }

    @Test
    void youngCompanyWithHighVolume() {
        Party young = pj().publiclyTraded(true).uboIdentified(true).incorporationDate(TODAY.minusMonths(6)).build();
        assertTrue(compute(young, inputs(List.of(), false, "150000", false, false, 0)).factors()
                .contains("PJ_RECENTE_VOLUME_ALTO"));
        assertFalse(compute(young, inputs(List.of(), false, "50000", false, false, 0)).factors()
                .contains("PJ_RECENTE_VOLUME_ALTO"));
        Party old = pj().publiclyTraded(true).incorporationDate(TODAY.minusMonths(30)).build();
        assertFalse(compute(old, inputs(List.of(), false, "150000", false, false, 0)).factors()
                .contains("PJ_RECENTE_VOLUME_ALTO"));
    }

    @Test
    void volumeIncompatibleWithIncomeOrRevenue() {
        Party pf = pf().declaredMonthlyIncome(new BigDecimal("2000")).build();
        assertTrue(compute(pf, inputs(List.of(), false, "6001", false, false, 0)).factors().contains("VOLUME_INCOMPATIVEL"));
        assertFalse(compute(pf, inputs(List.of(), false, "6000", false, false, 0)).factors().contains("VOLUME_INCOMPATIVEL"));
        Party pj = pj().publiclyTraded(true).annualRevenue(new BigDecimal("120000")).build();
        assertTrue(compute(pj, inputs(List.of(), false, "15001", false, false, 0)).factors().contains("VOLUME_INCOMPATIVEL"));
        assertFalse(compute(pj, inputs(List.of(), false, "15000", false, false, 0)).factors().contains("VOLUME_INCOMPATIVEL"));
    }

    @Test
    void confirmedCasesRaiseScore() {
        RiskAssessment one = compute(pf().build(), inputs(List.of(), false, "0", false, false, 1));
        assertTrue(one.factors().contains("ALERTAS_CONFIRMADOS(1)"));
        RiskAssessment many = compute(pf().build(), inputs(List.of(), false, "0", false, false, 5));
        assertTrue(many.score().compareTo(one.score()) > 0);
    }

    @Test
    void combinedFactorsReachHighAndCritical() {
        // UBO nao identificado (35) + receita irregular (40) = 75 -> HIGH com EDD
        RiskAssessment high = compute(pj().uboIdentified(false).receitaStatus("SUSPENSA").build(), none());
        assertEquals("HIGH", high.level());
        assertTrue(high.eddRequired());
        // + residencia em jurisdicao de alto risco (40) -> >= 90 CRITICAL
        RiskAssessment critical = compute(pj().uboIdentified(false).receitaStatus("SUSPENSA").build(),
                inputs(List.of(), false, "0", true, false, 0));
        assertEquals("CRITICAL", critical.level());
        assertEquals(0, new BigDecimal("100").compareTo(critical.score()), "teto 100");
    }

    @Test
    void levelBandsContainRepresentativeScores() {
        assertEquals("LOW", RiskScoringService.levelFor(25));
        assertEquals("MEDIUM", RiskScoringService.levelFor(50));
        assertEquals("HIGH", RiskScoringService.levelFor(75));
        assertEquals("CRITICAL", RiskScoringService.levelFor(100));
        assertEquals("LOW", RiskScoringService.levelFor(39));
        assertEquals("MEDIUM", RiskScoringService.levelFor(40));
        assertEquals("HIGH", RiskScoringService.levelFor(70));
        assertEquals("CRITICAL", RiskScoringService.levelFor(90));
    }

    @Test
    void nextReviewDatePerLevel() {
        assertEquals(TODAY.plusMonths(12), RiskScoringService.nextReviewDate("LOW", TODAY));
        assertEquals(TODAY.plusMonths(6), RiskScoringService.nextReviewDate("MEDIUM", TODAY));
        assertEquals(TODAY.plusMonths(3), RiskScoringService.nextReviewDate("HIGH", TODAY));
        assertEquals(TODAY.plusMonths(1), RiskScoringService.nextReviewDate("CRITICAL", TODAY));
    }
}
