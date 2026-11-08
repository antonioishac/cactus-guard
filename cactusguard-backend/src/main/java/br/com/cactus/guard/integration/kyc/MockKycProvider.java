package br.com.cactus.guard.integration.kyc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "integrations.kyc.provider", havingValue = "mock", matchIfMissing = true)
public class MockKycProvider implements KycProviderPort {

    @Override
    public boolean validateDocument(String document, String type) {
        log.info("Mock KYC validate document: {}", br.com.cactus.guard.common.util.DataMasker.maskDocument(document));
        return document != null && document.length() >= 11;
    }

    @Override
    public boolean validateBiometric(byte[] image, String document) {
        log.info("Mock KYC validate biometric for: {}", br.com.cactus.guard.common.util.DataMasker.maskDocument(document));
        return image != null && image.length > 0;
    }
}
