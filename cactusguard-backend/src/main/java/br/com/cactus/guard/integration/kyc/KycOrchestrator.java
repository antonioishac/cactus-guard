package br.com.cactus.guard.integration.kyc;

import br.com.cactus.guard.kyc.entity.KycProcess;
import br.com.cactus.guard.kyc.repository.KycProcessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycOrchestrator {

    private final KycProviderPort kycProvider;
    private final KycProcessRepository repository;

    @Transactional
    public boolean validateDocument(KycProcess process) {
        log.info("Orchestrating KYC validation for process: {}", process.getId());

        boolean valid = kycProvider.validateDocument(
                process.getParty().getDocument(),
                process.getParty().getType()
        );

        if (valid) {
            process.setStatus("DOCUMENT_VALIDATED");
            repository.save(process);
        }

        return valid;
    }

    public boolean validateBiometric(byte[] image, String document) {
        return kycProvider.validateBiometric(image, document);
    }
}
