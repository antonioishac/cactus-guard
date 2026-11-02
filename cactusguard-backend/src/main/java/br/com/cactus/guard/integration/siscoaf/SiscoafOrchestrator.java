package br.com.cactus.guard.integration.siscoaf;

import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import br.com.cactus.guard.siscoaf.repository.SiscoafCommunicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiscoafOrchestrator {

    private final SiscoafProviderPort siscoafProvider;
    private final SiscoafCommunicationRepository repository;

    @Transactional
    public SiscoafCommunication submit(SiscoafCommunication communication) {
        log.info("Orchestrating SISCOAF submit for: {}", communication.getId());

        String protocol = siscoafProvider.submit(communication);
        communication.setProtocolNumber(protocol);
        communication.setStatus("SUBMITTED");

        return repository.save(communication);
    }

    public String checkStatus(String protocolNumber) {
        return siscoafProvider.checkStatus(protocolNumber);
    }
}
