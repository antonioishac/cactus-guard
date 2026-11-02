package br.com.cactus.guard.integration.kyc;

public interface KycProviderPort {
    boolean validateDocument(String document, String type);
    boolean validateBiometric(byte[] image, String document);
}
