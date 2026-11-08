---
name: novo-modulo-backend
description: Scaffold de um novo módulo de domínio no cactusguard-backend (pacote br.com.cactus.guard.<modulo>) com entity, repository, DTOs record, service, controller, migration e testes, seguindo a spec 047. Use ao iniciar um módulo/agregado novo, não para endpoints em módulo existente (use novo-endpoint).
argument-hint: "<modulo> <Entidade> [spec NNN]"
---

Módulo/entidade: $ARGUMENTS

1. Leia a spec do módulo e `specs/047-padroes-tecnicos-backend.md` (seções Arquitetura e Organização de pacotes). Hexagonal (`domain/application/ports/adapters`) só se for screening, regras/monitoramento, SISCOAF ou ingestão; senão camadas convencionais.
2. Copie o padrão de um módulo irmão pequeno (ex.: `party/` ou `case_/`) — leia 1 arquivo de cada camada, não o módulo todo.
3. Crie, nesta ordem:
   - [ ] migration nova (skill `/nova-migration`) com índices e `*_hash` para PII
   - [ ] `entity/<Entidade>.java` (UUID, Lombok `@Getter/@Setter/@Builder`, auditoria de datas)
   - [ ] `repository/<Entidade>Repository.java` (consultas paginadas; full-text via `tsvector`, não `LIKE`)
   - [ ] `dto/<Entidade>Request.java` e `<Entidade>Response.java` como `record` + Bean Validation; PII mascarada no Response
   - [ ] `service/<Entidade>Service.java` (`@Transactional`, `AuditService.log` em ações relevantes, `DataAccessService.log` ao ler PII)
   - [ ] `controller/<Entidade>Controller.java` (`/api/v1/<recurso>`, `@PreAuthorize` por endpoint, `Pageable`, `@Tag/@Operation`)
   - [ ] testes `src/test/java/br/com/cactus/guard/<modulo>/<Entidade><Cenario>Test.java` (feliz, validação, 403 por role, regra regulatória)
4. Rode `/rodar-testes <Entidade>*Test`.
5. Peça `/revisao-regulatoria` se o módulo tocar PII, alertas, cases, regras ou COAF.
