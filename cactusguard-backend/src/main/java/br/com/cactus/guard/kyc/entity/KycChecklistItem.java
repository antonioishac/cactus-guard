package br.com.cactus.guard.kyc.entity;

import br.com.cactus.guard.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyc_checklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false)
    private KycProcess process;

    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    @Column(name = "item_description")
    private String itemDescription;

    @Column(nullable = false)
    private boolean required = true;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private User completedBy;
}
