package br.com.cactus.guard.alert.entity;

import br.com.cactus.guard.transaction.entity.Transaction;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Vinculo entre um alerta e as transacoes que o geraram (V25).
 * Permite ao analista ver exatamente as operacoes avaliadas e ao dossie/COAF
 * referenciar as operacoes comunicadas.
 */
@Entity
@Table(name = "alert_transactions",
       uniqueConstraints = @UniqueConstraint(name = "uq_alert_transactions", columnNames = {"alert_id", "transaction_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertTransaction extends br.com.cactus.guard.tenant.TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id", nullable = false)
    private Alert alert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
