package br.com.cactus.guard.transaction.entity;

import br.com.cactus.guard.party.entity.Party;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction extends br.com.cactus.guard.tenant.TenantScoped {

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
    @Builder.Default
    private String currency = "BRL";

    @Column(name = "counterparty_name")
    private String counterpartyName;

    // PII cifrada (V30): busca somente por counterparty_document_hash
    @Convert(converter = br.com.cactus.guard.crypto.EncryptedStringConverter.class)
    @Column(name = "counterparty_document", length = 512)
    private String counterpartyDocument;

    @Column(length = 30)
    private String channel;

    @Column(nullable = false, length = 20)
    @Builder.Default
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

    // ===================== Campos PLD (V24) =====================

    /** IN ou OUT, do ponto de vista da parte (party). */
    @Column(length = 3)
    private String direction;

    @Column(name = "is_cash", nullable = false)
    @Builder.Default
    private boolean cash = false;

    /** PIX, TED, DOC, BOLETO, CASH, CARD, FX, CRYPTO, INTERNAL. */
    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    /** Identificador fim-a-fim do PIX. */
    @Column(name = "end_to_end_id", length = 64)
    private String endToEndId;

    @Column(name = "account_number", length = 30)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_party_id")
    private Party counterpartyParty;

    @Column(name = "counterparty_document_hash", length = 64)
    private String counterpartyDocumentHash;

    @Column(name = "counterparty_bank_ispb", length = 8)
    private String counterpartyBankIspb;

    @Column(name = "counterparty_branch", length = 10)
    private String counterpartyBranch;

    @Column(name = "counterparty_account", length = 30)
    private String counterpartyAccount;

    @Column(name = "counterparty_country", length = 3)
    private String counterpartyCountry;

    @Column(name = "amount_brl", precision = 18, scale = 2)
    private BigDecimal amountBrl;

    @Column(name = "fx_rate", precision = 18, scale = 8)
    private BigDecimal fxRate;

    /** Lote de ingestao/migracao de origem. */
    @Column(name = "source_batch_id")
    private UUID sourceBatchId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata;

    /** Provisionamento de saque em especie. */
    @Column(name = "cash_withdrawal_provision", nullable = false)
    @Builder.Default
    private boolean cashWithdrawalProvision = false;

    @Column(name = "provision_scheduled_date")
    private LocalDate provisionScheduledDate;

    /** Lancamento original estornado/devolvido por esta transacao (MED, chargeback). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id")
    private Transaction reversalOf;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
