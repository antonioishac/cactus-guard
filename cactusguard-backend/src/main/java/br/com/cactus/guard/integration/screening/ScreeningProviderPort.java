package br.com.cactus.guard.integration.screening;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import java.util.List;

public interface ScreeningProviderPort {
    ScreeningResult screen(Party party, List<String> listCodes);
    List<String> availableLists();
}
