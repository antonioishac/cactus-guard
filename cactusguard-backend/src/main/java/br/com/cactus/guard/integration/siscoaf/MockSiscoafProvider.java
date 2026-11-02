package br.com.cactus.guard.integration.siscoaf;

import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "integrations.siscoaf.provider", havingValue = "mock", matchIfMissing = true)
public class MockSiscoafProvider implements SiscoafProviderPort {

    @Override
    public String submit(SiscoafCommunication communication) {
        log.info("Mock SISCOAF submit for case: {}", communication.getCaseEntity() != null ? communication.getCaseEntity().getId() : "N/A");
        return "MOCK-" + System.currentTimeMillis();
    }

    @Override
    public String checkStatus(String protocolNumber) {
        return "ACKNOWLEDGED";
    }
}
