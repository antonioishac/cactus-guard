# Spec 036 — Cache Distribuído (Redis)

## Decisão

Regras de monitoramento e listas de screening devem ser cacheadas no **Redis**, não em cache local (Caffeine/Guava).

## Motivo

Em ambiente com múltiplos pods (Kubernetes ou similar), cache local gera inconsistência:
- Um pod atualiza a regra.
- Outros pods continuam usando a versão antiga até o TTL local expirar.
- Avaliação de regras diverge entre instâncias.

Redis centraliza o cache. Todos os pods leem e invalidam a mesma fonte.

## Escopo do cache

| Dado | Chave Redis | TTL | Invalidação |
|------|-------------|-----|-------------|
| Regras ativas | `rules:active` | 5 min | Ao fazer toggle |
| Lista de screening por código | `screening:list:{code}` | 10 min | Ao atualizar lista |
| Resultado de screening por parte | `screening:party:{partyId}` | 2 min | Ao revisar match |

## Comportamento

- **Leitura**: tenta Redis primeiro. Se miss ou Redis indisponível, busca no banco e popula o cache.
- **Escrita/Toggle**: invalida a chave correspondente imediatamente.
- **Fallback**: se Redis estiver down, a aplicação continua funcionando (só perde o benefício de cache).

## Configuração

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

## Invalidação obrigatória

Qualquer operação que altere regras deve invalidar o cache imediatamente:

| Operação | Ação no cache |
|----------|---------------|
| Criar regra | `@CacheEvict` em `rules` (allEntries) |
| Atualizar regra | `@CacheEvict` em `rules` (allEntries) |
| Ativar/Desativar | `@CacheEvict` em `rules` (allEntries) |
| Alterar threshold | `@CacheEvict` em `rules` (allEntries) |

Após a invalidação, a próxima leitura (`findAllActive` ou `findByCategory`) busca do banco e popula o cache novamente.

## Regra de ouro

**Nenhuma alteração de regra pode deixar o cache desatualizado.** O `@Caching(evict = ...)` deve estar presente em todos os métodos de escrita.

- Não usar `@Cacheable` com cache local em regras ou screening.
- Sempre invalidar explicitamente ao alterar regra ou lista.
- Monitorar hit/miss ratio em produção.
- Em testes de integração, usar Redis embutido ou mock.
