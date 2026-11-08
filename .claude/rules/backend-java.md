---
paths:
  - "cactusguard-backend/src/**/*.java"
  - "cactusguard-backend/pom.xml"
  - "cactusguard-backend/src/main/resources/*.yml"
---

# Backend Java (Spring Boot 4 / Java 25)

- Pacote `br.com.cactus.guard.<modulo>/{controller,service,entity,dto,repository}`; hexagonal só em screening, rule, siscoaf, ingestão (spec 047).
- DTO = `record` + Bean Validation; entity nunca sai do controller; MapStruct/Lombok conforme spec 047.
- Todo endpoint: `@PreAuthorize` (ADMIN, SUPERVISOR, ANALYST), `Pageable` em listas, springdoc `@Tag/@Operation`.
- Ação relevante → `AuditService.log`; leitura de PII → `DataAccessService.log`; resposta com `DataMasker`.
- Logs: `@Slf4j`, sem PII em claro, sem `System.out`/`printStackTrace`.
- Integrações externas: Resilience4j + fallback; segredos só por variável de ambiente.
- Testes: `@SpringBootTest` + `@ActiveProfiles("test")` (H2, Flyway off). Rode por módulo: `JAVA_HOME=/c/Java/jdk-25.0.2 mvn -q test -Dtest='Modulo*Test'` sem `| tail`.
- Skills: `/novo-endpoint`, `/novo-modulo-backend`, `/rodar-testes`.
