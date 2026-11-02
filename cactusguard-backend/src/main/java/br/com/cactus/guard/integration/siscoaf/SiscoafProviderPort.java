package br.com.cactus.guard.integration.siscoaf;

import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;

public interface SiscoafProviderPort {
    String submit(SiscoafCommunication communication);
    String checkStatus(String protocolNumber);
}
