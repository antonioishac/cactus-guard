package br.com.cactus.guard.risk.service;

import br.com.cactus.guard.risk.entity.RiskHistory;
import br.com.cactus.guard.risk.repository.RiskHistoryRepository;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RiskHistoryService {

    private final RiskHistoryRepository repository;

    @Transactional
    public RiskHistory record(Party party, BigDecimal previousScore, BigDecimal newScore,
                              String previousLevel, String newLevel, String reason, User changedBy) {
        RiskHistory history = RiskHistory.builder()
                .party(party)
                .previousScore(previousScore)
                .newScore(newScore)
                .previousLevel(previousLevel)
                .newLevel(newLevel)
                .reason(reason)
                .changedBy(changedBy)
                .build();
        return repository.save(history);
    }

    public Page<RiskHistory> findByParty(UUID partyId, Pageable pageable) {
        return repository.findByPartyIdOrderByCreatedAtDesc(partyId, pageable);
    }
}
