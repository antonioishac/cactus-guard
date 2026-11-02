package br.com.cactus.guard.transaction.entity;

import br.com.cactus.guard.party.entity.Party;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "BRL";

    @Column(name = "counterparty_name")
    private String counterpartyName;

    @Column(name = "counterparty_document", length = 20)
    private String counterpartyDocument;

    @Column(length = 30)
    private String channel;

    @Column(nullable = false, length = 20)
    private String status = "COMPLETED";

    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;

    @Column(name = "origin_country", length = 3)
    private String originCountry;

    @Column(name = "destination_country", length = 3)
    private String destinationCountry;

    @Column(length = 200)
    private String purpose;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
