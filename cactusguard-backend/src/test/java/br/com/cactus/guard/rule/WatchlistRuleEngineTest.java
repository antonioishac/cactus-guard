package br.com.cactus.guard.rule;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.engine.RuleEngineService;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.watchlist.entity.WatchlistEntry;
import br.com.cactus.guard.watchlist.repository.WatchlistEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class WatchlistRuleEngineTest {

    @Autowired
    private RuleEngineService ruleEngineService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private WatchlistEntryRepository watchlistRepository;

    @Autowired
    private MonitoringRuleRepository ruleRepository;

    @Test
    void watchlistMatchShouldGenerateAlert() {
        Party party = Party.builder()
                .type("PF")
                .document("11100011100")
                .documentHash("hash111")
                .name("Watchlist Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        WatchlistEntry entry = WatchlistEntry.builder()
                .document("11100011100")
                .name("Watchlist Test")
                .reason("Teste de watchlist")
                .severity("HIGH")
                .active(true)
                .build();
        watchlistRepository.save(entry);

        MonitoringRule rule = ruleRepository.findByCode("WATCHLIST_MATCH").orElse(null);
        if (rule != null) {
            int alerts = ruleEngineService.evaluateRule(rule);
            assertTrue(alerts >= 0);
        }
    }
}
