package br.com.cactus.guard.party.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "party_relationships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_party_id", nullable = false)
    private Party relatedParty;

    @Column(name = "relationship_type", nullable = false, length = 50)
    private String relationshipType;

    @Column(length = 200)
    private String description;

    @Builder.Default
    private boolean active = true;

    /** Inicio da vigencia do vinculo (historico de relacionados a PEP - art. 19, V23). */
    @Column(name = "valid_from")
    private LocalDate validFrom;

    /** Fim da vigencia; NULL = vigente. Apenas um vinculo vigente por (party, related, type). */
    @Column(name = "valid_to")
    private LocalDate validTo;

    /** Grau de parentesco, fonte da informacao, participacao conjunta etc. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private Map<String, Object> details;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
