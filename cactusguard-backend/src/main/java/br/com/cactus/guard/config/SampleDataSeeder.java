package br.com.cactus.guard.config;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.transaction.entity.Transaction;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class SampleDataSeeder implements CommandLineRunner {

    private final PartyRepository partyRepository;
    private final TransactionRepository transactionRepository;

    @Override
    public void run(String... args) {
        if (partyRepository.count() > 0) return;

        log.info("Seeding sample data...");

        Party party = Party.builder()
                .type("PF")
                .document("12345678901")
                .name("João da Silva")
                .email("joao@email.com")
                .status("ACTIVE")
                .riskLevel("PENDING")
                .build();
        party = partyRepository.save(party);

        Transaction txn = Transaction.builder()
                .party(party)
                .type("PIX")
                .amount(new BigDecimal("75000.00"))
                .currency("BRL")
                .counterpartyName("Empresa XYZ")
                .channel("APP")
                .status("COMPLETED")
                .transactionDate(Instant.now())
                .build();
        transactionRepository.save(txn);

        log.info("Sample data created: 1 party + 1 transaction");
    }
}
