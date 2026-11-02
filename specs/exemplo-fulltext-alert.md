# Exemplo: Full-Text Search na entidade Alert

## 1. Migration Flyway

```sql
-- Vxxx__add_alert_fulltext_search.sql

ALTER TABLE alert
    ADD COLUMN search_vector tsvector;

-- Preenche o vetor (descrição + tipo + nome da parte, se disponível via join ou campo desnormalizado)
UPDATE alert a
SET search_vector = to_tsvector('portuguese',
    coalesce(a.description, '') || ' ' ||
    coalesce(a.alert_type, '') || ' ' ||
    coalesce(a.party_name, '')   -- campo desnormalizado recomendado para performance
);

CREATE INDEX idx_alert_search_vector ON alert USING GIN (search_vector);

-- Trigger de manutenção
CREATE OR REPLACE FUNCTION alert_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('portuguese',
        coalesce(NEW.description, '') || ' ' ||
        coalesce(NEW.alert_type, '') || ' ' ||
        coalesce(NEW.party_name, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_alert_search_vector
    BEFORE INSERT OR UPDATE OF description, alert_type, party_name
    ON alert
    FOR EACH ROW
    EXECUTE FUNCTION alert_search_vector_update();
```

> Recomendação: manter um campo `party_name` (ou `search_text`) desnormalizado na tabela `alert` para evitar join na busca textual.

## 2. Entidade JPA

```java
@Entity
@Table(name = "alert")
public class Alert {

    @Id
    private UUID id;

    @Column(name = "description")
    private String description;

    @Column(name = "alert_type")
    private String alertType;

    @Column(name = "party_name")
    private String partyName;   // desnormalizado para busca

    @Column(name = "search_vector", columnDefinition = "tsvector", insertable = false, updatable = false)
    private String searchVector;

    // demais campos e relacionamentos
}
```

## 3. Repository

```java
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    @Query(value = """
        SELECT * FROM alert
        WHERE tenant_id = :tenantId
          AND search_vector @@ plainto_tsquery('portuguese', :term)
        ORDER BY ts_rank(search_vector, plainto_tsquery('portuguese', :term)) DESC,
                 triggered_at DESC
        """, nativeQuery = true)
    Page<Alert> searchByTerm(@Param("tenantId") UUID tenantId,
                             @Param("term") String term,
                             Pageable pageable);
}
```

## 4. Uso no Service / Controller

O endpoint `GET /api/v1/alerts?q=termo` deve:
- Se `q` estiver presente → usar `searchByTerm`.
- Caso contrário → usar a query normal com filtros e paginação.

## 5. Observações de performance e consistência

- Campo `party_name` desnormalizado evita join na busca.
- Trigger garante consistência do `tsvector`.
- Índice GIN + filtro de `tenant_id` mantém a busca rápida mesmo com alto volume.
- Em listagens muito grandes, combinar full-text com outros filtros (status, severidade, data) antes do ranking.
- Sempre validar com `EXPLAIN ANALYZE` após carga de dados real.
