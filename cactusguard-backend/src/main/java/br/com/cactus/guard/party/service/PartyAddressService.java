package br.com.cactus.guard.party.service;

import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.entity.PartyAddress;
import br.com.cactus.guard.party.repository.PartyAddressRepository;
import br.com.cactus.guard.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartyAddressService {

    private final PartyAddressRepository repository;
    private final PartyRepository partyRepository;
    private final DataAccessService dataAccessService;

    @Transactional
    public PartyAddress add(UUID partyId, String type, String street, String number, String city, String state, String zipCode, boolean primary) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException("Parte não encontrada"));

        PartyAddress address = PartyAddress.builder()
                .party(party)
                .type(type)
                .street(street)
                .number(number)
                .city(city)
                .state(state)
                .zipCode(zipCode)
                .primary(primary)
                .build();

        return repository.save(address);
    }

    public List<PartyAddress> findByParty(UUID partyId) {
        return repository.findByPartyId(partyId);
    }

    @Transactional
    public void remove(UUID id) {
        repository.deleteById(id);
    }
}
