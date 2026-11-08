# 047 — Padrões técnicos e arquitetura do backend (CactusGuard)

> Renumerada de `007-padroes-tecnicos-backend.md` (havia dois arquivos 007). Status de implementação de cada decisão: ver `README.md` → "Status de implementação vs specs".

Complementa a visão geral do projeto. Define como o código Java do **CactusGuard** é organizado, quais padrões usar e quando.

## Versões e linguagem

- **Java 25+** com uso deliberado de recursos modernos: `records` para DTOs imutáveis, `Optional` em buscas, streams/lambdas com critério, virtual threads habilitadas para I/O-bound (screening, chamadas externas, consultas concorrentes), pattern matching e sealed classes onde modelam bem conjuntos fechados de estados (ex.: resultado de screening, status de alerta, decisão de case).
- **Spring Boot 4.x** (versão estável mais recente; mínimo 4.1.2) como framework, alinhado ao Java 25+ (suporte nativo a virtual threads).

## Arquitetura: monolito modular + hexagonal seletivo

Hexagonal (ports & adapters) **apenas** nos módulos com regra de negócio real e fronteira externa instável ou configurável:

- **Screening**
- **Motor de Regras / Monitoramento**
- **Comunicação SISCOAF**
- **Ingestão de Dados**

Os demais módulos (KYC/cadastro, Case Management, Relatórios, Auditoria, Alertas operacionais) usam camadas convencionais (controller → service → repository) para evitar over-engineering.

Onde hexagonal se aplica:
- `domain/` — modelo de domínio e regras puras (sem Spring/JPA).
- `application/` — casos de uso que orquestram o domínio.
- `ports/` — interfaces (ex.: `ScreeningPort`, `RuleEnginePort`, `SiscoafPort`, `DataIngestionPort`).
- `adapters/` — implementações concretas (entrada: controllers; saída: clientes HTTP, repositórios, produtores Kafka).

## Package base

**`br.com.cactus.guard`** + o módulo correspondente.  
Todo código do backend nasce sob este pacote raiz.

## Organização de pacotes (módulos convencionais)

```
br.com.cactus.guard.<modulo>/          # ex: br.com.cactus.guard.kyc, br.com.cactus.guard.case
├── controller/
├── service/
├── entity/
├── model/
├── dto/
├── repository/
└── mapper/
```

## Organização de pacotes (módulos com hexagonal)

```
br.com.cactus.guard.<modulo>/          # ex: br.com.cactus.guard.screening, br.com.cactus.guard.monitoring
├── domain/
├── application/
├── ports/
└── adapters/
```

O módulo compartilhado (`common`) também vive sob `br.com.cactus.guard.common`.

## Mapeamento e Lombok

- Todo mapeamento entidade ↔ DTO via **MapStruct**.
- Lombok (`@Getter`/`@Setter`, `@Builder`, `@RequiredArgsConstructor`) nas entidades e services.
- Preferir `record` em DTOs imutáveis.

## Padrões de projeto

- **Strategy**: para variações de regras de monitoramento, classificação de risco e validações de screening.
- **Circuit breaker**: **Resilience4j** em toda chamada a serviço externo (provedores de listas, SISCOAF, APIs de dados).
- **Eventos de domínio**: mudanças relevantes publicadas como eventos.  
  **Kafka é a decisão de arquitetura** (não apenas Spring ApplicationEvent). Status: **pendente** — o código atual não publica eventos em Kafka.  
  Requisitos obrigatórios:
  - DLQ em todo consumer.
  - Retentativas com backoff.
  - Controle e reprocessamento de mensagens em DLQ (com motivo do erro registrado).
  - Nunca descartar mensagem silenciosamente.

## Banco de dados

- **Migrations: Flyway** (SQL puro versionado).
- Índices obrigatórios desde a criação da tabela em campos de filtro, busca e ordenação (especialmente em `Transaction`, `Alert`, `Party`, `ScreeningResult`).
- **Paginação obrigatória** (`Pageable`) em todas as listagens.
- Busca e filtros pelos campos principais de cada entidade relevante.
- Revisar `EXPLAIN ANALYZE` em queries de relatório e monitoramento antes de considerar a feature pronta.

### Full-Text Search (PostgreSQL)

Para buscas textuais (nome, documento, descrição de alerta, justificativa etc.), **não usar `LIKE`/`ILIKE` como padrão**.

- Usar **Full-Text Search** nativo do PostgreSQL (`tsvector` + `tsquery`).
- Criar coluna gerada ou coluna materializada `search_vector tsvector` nas entidades que precisam de busca textual.
- Criar índice GIN sobre a coluna `tsvector`.
- No JPA/Hibernate:
  - Mapear a coluna `tsvector` (pode ser somente leitura).
  - Usar `@Formula` ou query nativa / Criteria com função `to_tsvector` / `plainto_tsquery` quando necessário.
  - Preferir queries nativas ou Specification para buscas full-text complexas.
- Manter `LIKE` apenas para casos pontuais e de baixo volume (ex.: busca exata de documento com prefixo curto).
- Toda busca textual exposta em API deve usar full-text search.

## Autenticação e autorização

- **Spring Security + OAuth2 + JWT**.
- Backend atua como **OAuth2 Resource Server**.
- **Provedor inicial**: JWT emitido pelo próprio sistema ou Authorization Server leve no MVP.
- **Integração futura com Azure AD**: arquitetura preparada para trocar o provedor OIDC sem impacto no restante do sistema (configuração por tenant ou por ambiente).
- **Multi-tenancy**: claim `tenant_id` no JWT.  
  Schema único + coluna `tenant_id` obrigatória e indexada + filtro automático via Hibernate `@Filter`/`@FilterDef` habilitado por request.  
  Proibido espalhar `WHERE tenant_id = ?` manualmente nos repositories.  
  Status: **pendente** — entidades atuais não têm `tenant_id` nem filtro; ver `README.md`. A spec 015 cita RLS do PostgreSQL como camada adicional (defesa em profundidade) sobre o filtro Hibernate.

## Tratamento de erros e logging

- `@ControllerAdvice` + `@ExceptionHandler` centralizado no módulo `common`.
- Resposta de erro padronizada (código, mensagem, timestamp, trace id).
- Log estruturado com **trace id / correlation id** propagado inclusive em chamadas externas e em eventos Kafka.
- Padrão consistente em todos os módulos (incluindo logs auditáveis de PLD).

## Documentação OpenAPI

- springdoc com `@Tag` / `@Operation` / `@SecurityRequirement`.
- Respostas de erro padrão (400, 401, 403, 404, 422, 500) documentadas **globalmente** via customizer no módulo `common` (sem anotação manual repetida em cada controller).
- Anotar `@ApiResponse` manualmente apenas para erros específicos de um endpoint.

## Observações específicas para PLD

- Toda ação relevante (criação de alerta, decisão de case, comunicação ao COAF, alteração de regra, mudança de status PEP) deve gerar evento e registro em `AuditLog`.
- O motor de regras e o screening devem ser testáveis isoladamente (graças ao hexagonal).
- Performance e particionamento de tabelas de alto volume (transações, alertas, logs) devem ser considerados desde as primeiras migrations.
