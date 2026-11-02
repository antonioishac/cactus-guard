package br.com.cactus.guard.party.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
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

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
