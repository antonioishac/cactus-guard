package br.com.cactus.guard.transaction.service;

import br.com.cactus.guard.transaction.entity.Transaction;
import br.com.cactus.guard.transaction.entity.TransactionProfile;
import br.com.cactus.guard.transaction.repository.TransactionProfileRepository;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProfileService {

    private final TransactionProfileRepository profileRepository;
    private final TransactionRepository transactionRepository;
    private final PartyRepository partyRepository;

    @Transactional
    public TransactionProfile calculate(UUID partyId) {
        Party party = partyRepository.findById(partyId).orElseThrow();
        Instant since = Instant.now().minus(90, ChronoUnit.DAYS);
        List<Transaction> txns = transactionRepository.findByPartyIdAndTransactionDateAfter(partyId, since);

        if (txns.isEmpty()) {
            return profileRepository.findByPartyId(partyId).orElse(null);
        }

        BigDecimal totalVolume = txns.stream()
                .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgVolume = totalVolume.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        int avgCount = txns.size() / 3;
        BigDecimal maxAmount = txns.stream()
                .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        TransactionProfile profile = profileRepository.findByPartyId(partyId)
                .orElse(TransactionProfile.builder().party(party).build());

        profile.setAvgMonthlyVolume(avgVolume);
        profile.setAvgMonthlyCount(avgCount);
        profile.setMaxSingleAmount(maxAmount);
        profile.setLastCalculatedAt(Instant.now());

        return profileRepository.save(profile);
    }

    public TransactionProfile findByParty(UUID partyId) {
        return profileRepository.findByPartyId(partyId).orElse(null);
    }
}
