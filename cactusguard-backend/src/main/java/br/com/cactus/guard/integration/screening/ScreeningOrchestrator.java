package br.com.cactus.guard.integration.screening;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScreeningOrchestrator {

    private final ScreeningProviderPort screeningProvider;
    private final ScreeningResultRepository resultRepository;

    @Transactional
    public ScreeningResult screenParty(Party party, List<String> listCodes) {
        log.info("Orchestrating screening for party: {}", party.getId());

        ScreeningResult result = screeningProvider.screen(party, listCodes);
        return resultRepository.save(result);
    }

    public List<String> availableLists() {
        return screeningProvider.availableLists();
    }
}
