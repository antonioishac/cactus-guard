# Exemplo: Full-Text Search na entidade Party

## 1. Migration Flyway

```sql
-- Vxxx__add_party_fulltext_search.sql

ALTER TABLE party
    ADD COLUMN search_vector tsvector;

-- Preenche o vetor de busca (nome + documento)
UPDATE party
SET search_vector = to_tsvector('portuguese', coalesce(name, '') || ' ' || coalesce(document, ''));

-- Índice GIN para performance
CREATE INDEX idx_party_search_vector ON party USING GIN (search_vector);

-- Trigger para manter o search_vector atualizado automaticamente
CREATE OR REPLACE FUNCTION party_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('portuguese', coalesce(NEW.name, '') || ' ' || coalesce(NEW.document, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_party_search_vector
    BEFORE INSERT OR UPDATE OF name, document
    ON party
    FOR EACH ROW
    EXECUTE FUNCTION party_search_vector_update();
```

## 2. Entidade JPA

```java
@Entity
@Table(name = "party")
public class Party {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String document;

    // Outros campos...

    @Column(name = "search_vector", columnDefinition = "tsvector", insertable = false, updatable = false)
    private String searchVector;

    // getters e setters
}
```

## 3. Repository (query nativa)

```java
public interface PartyRepository extends JpaRepository<Party, UUID> {

    @Query(value = """
        SELECT * FROM party
        WHERE tenant_id = :tenantId
          AND search_vector @@ plainto_tsquery('portuguese', :term)
        ORDER BY ts_rank(search_vector, plainto_tsquery('portuguese', :term)) DESC
        """, nativeQuery = true)
    Page<Party> searchByTerm(@Param("tenantId") UUID tenantId,
                             @Param("term") String term,
                             Pageable pageable);
}
```

## 4. Observações

- O trigger mantém o `search_vector` sempre atualizado.
- A busca usa `plainto_tsquery` (mais amigável para o usuário).
- O ranking (`ts_rank`) ordena os resultados mais relevantes primeiro.
- O filtro de `tenant_id` continua obrigatório (multi-tenancy).
- Em produção, avaliar se vale a pena incluir mais campos no `tsvector` (ex.: nome fantasia, e-mail).
