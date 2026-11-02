package br.com.cactus.guard.integration.screening;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "integrations.screening.provider", havingValue = "mock", matchIfMissing = true)
public class MockScreeningProvider implements ScreeningProviderPort {

    @Override
    public ScreeningResult screen(Party party, List<String> listCodes) {
        log.info("Mock screening for party: {}", party.getDocument());
        return ScreeningResult.builder()
                .party(party)
                .listCode(listCodes.isEmpty() ? "PEP_BR" : listCodes.get(0))
                .matchScore(new BigDecimal("0.0"))
                .status("NO_MATCH")
                .build();
    }

    @Override
    public List<String> availableLists() {
        return List.of("PEP_BR", "UN_SANCTIONS", "OFAC");
    }
}
