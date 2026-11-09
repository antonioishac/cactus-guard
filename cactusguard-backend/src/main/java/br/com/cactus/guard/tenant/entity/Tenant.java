package br.com.cactus.guard.tenant.entity;

import br.com.cactus.guard.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cliente da plataforma (instituicao regulada). Catalogo GLOBAL: nao tem tenant_id nem RLS (V33). */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";

    /** Atribuido pela aplicacao (os tenants default/platform tem ids fixos). */
    @Id
    private UUID id;

    /** Slug unico (minusculas, digitos e hifen). */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 512)
    private String cnpj;

    @Column(name = "cnpj_hash", length = 64)
    private String cnpjHash;

    /** BANK, PAYMENT_INSTITUTION, SCD, SEP, BROKER_CVM, INSURER_SUSEP, VASP, OTHER. */
    @Column(name = "regulatory_segment", nullable = false, length = 30)
    private String regulatorySegment;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private Map<String, Object> settings = new HashMap<>();

    /** 1 = hashes de documento com pepper global (legado); 2 = pepper por tenant (HKDF). Ver DocumentHashMigrationRunner. */
    @Column(name = "hash_version", nullable = false)
    @Builder.Default
    private int hashVersion = 2;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
