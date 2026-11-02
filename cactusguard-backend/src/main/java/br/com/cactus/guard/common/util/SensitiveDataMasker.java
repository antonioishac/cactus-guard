package br.com.cactus.guard.common.util;

import org.springframework.stereotype.Component;

@Component
public class SensitiveDataMasker {

    public String maskDocument(String document) {
        return DataMasker.maskDocument(document);
    }

    public String maskEmail(String email) {
        return DataMasker.maskEmail(email);
    }

    public String maskPhone(String phone) {
        return DataMasker.maskPhone(phone);
    }
}
