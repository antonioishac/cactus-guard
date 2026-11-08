package br.com.cactus.guard.health;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.health.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DashboardAlignmentTest {

    @Autowired private DashboardService dashboard;
    @Autowired private AlertRepository alertRepository;
    @Autowired private CaseRepository caseRepository;

    @Test
    void caseSlaCountsAreDeltaConsistentPerRegulatoryDeadline() {
        var before = dashboard.caseSla();
        Instant now = Instant.now();
        caseRepository.save(Case.builder().status("OPEN").analysisDeadline(now.plus(Duration.ofDays(30))).build());
        caseRepository.save(Case.builder().status("IN_ANALYSIS").analysisDeadline(now.plus(Duration.ofDays(2))).build());
        caseRepository.save(Case.builder().status("ESCALATED").analysisDeadline(now.minus(Duration.ofDays(1))).build());
        caseRepository.save(Case.builder().status("COMMUNICATE").communicationDeadline(now.minus(Duration.ofHours(1))).build());
        caseRepository.save(Case.builder().status("PENDING_APPROVAL").analysisDeadline(now.plus(Duration.ofDays(20))).build());
        caseRepository.save(Case.builder().status("ARCHIVED").analysisDeadline(now.minus(Duration.ofDays(9))).build());

        var after = dashboard.caseSla();
        assertEquals(2, after.analysis().onTime() - before.analysis().onTime());
        assertEquals(1, after.analysis().dueSoon() - before.analysis().dueSoon());
        assertEquals(1, after.analysis().overdue() - before.analysis().overdue());
        assertEquals(1, after.communication().overdue() - before.communication().overdue());
        assertEquals(1, after.pendingApproval() - before.pendingApproval());
    }

    @Test
    void selectionBucketsUseAlertSelectionDeadline() {
        var before = dashboard.caseSla();
        Instant now = Instant.now();
        alertRepository.save(Alert.builder().type("T").severity("LOW").status("OPEN").title("a")
                .selectionDeadline(now.minus(Duration.ofDays(1))).createdAt(now).build());
        alertRepository.save(Alert.builder().type("T").severity("LOW").status("IN_ANALYSIS").title("a")
                .selectionDeadline(now.plus(Duration.ofDays(1))).createdAt(now).build());
        var after = dashboard.caseSla();
        assertEquals(1, after.selection().overdue() - before.selection().overdue());
        assertEquals(1, after.selection().dueSoon() - before.selection().dueSoon());
    }

    @Test
    void severityDistributionAlwaysListsAllSeverities() {
        alertRepository.save(Alert.builder().type("T").severity("CRITICAL").status("OPEN").title("a")
                .createdAt(Instant.now()).build());
        var dist = dashboard.severityDistribution();
        assertEquals(4, dist.size());
        assertTrue(dist.stream().filter(s -> s.severity().equals("CRITICAL")).findFirst().orElseThrow().count() >= 1);
    }

    @Test
    void alertsChartFillsEmptyDaysAndCountsToday() {
        alertRepository.save(Alert.builder().type("T").severity("LOW").status("OPEN").title("a")
                .createdAt(Instant.now()).build());
        var chart = dashboard.alertsChart(7);
        assertEquals(7, chart.size());
        assertTrue(chart.get(6).total() >= 1);
        assertEquals(1, dashboard.alertsChart(0).size());
        assertEquals(DashboardService.MAX_CHART_DAYS, dashboard.alertsChart(10_000).size());
    }

    @Test
    void metricsExposeAlertsByStatus() {
        alertRepository.save(Alert.builder().type("T").severity("LOW").status("OPEN").title("a")
                .createdAt(Instant.now()).build());
        Map<String, Object> m = dashboard.metrics();
        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) m.get("alertsByStatus");
        assertTrue(byStatus.get("OPEN") >= 1);
        assertTrue(byStatus.containsKey("FALSE_POSITIVE"));
        assertNotNull(m.get("activePeps"));
    }
}
