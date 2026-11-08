---
name: novo-endpoint
description: Adiciona um endpoint REST a um módulo existente do cactusguard-backend com DTO record, @PreAuthorize por role, auditoria (AuditService/DataAccessService), OpenAPI, paginação, mascaramento de PII e teste. Use para qualquer novo GET/POST/PUT/PATCH/DELETE em controller existente.
argument-hint: "<METODO> /api/v1/<recurso> <módulo> [roles]"
---

Endpoint: $ARGUMENTS

1. Confira o contrato em `specs/033-contratos-api.md` e as roles na spec 020 (ADMIN, SUPERVISOR, ANALYST). Leia o controller e o service do módulo (só eles).
2. Checklist:
   - [ ] rota `/api/v1/...`, verbo e status HTTP corretos (201 em criação, 204 em delete)
   - [ ] `@PreAuthorize("hasAnyRole(...)")` com o MENOR conjunto de roles; decisões/COAF/regras só SUPERVISOR/ADMIN
   - [ ] Request/Response como `record`; `@Valid`; nunca expor entity
   - [ ] listagem com `Pageable` e ordenação padrão; filtros por campos indexados; texto via full-text
   - [ ] `{id}`: verifique acesso ao recurso (anti-IDOR)
   - [ ] PII mascarada no Response (`DataMasker`); leitura de PII → `DataAccessService.log(entityType, id, campos, motivo, ip)`
   - [ ] ação de negócio → `AuditService.log(acao, entityType, id, antes, depois)`; justificativa obrigatória em decisões
   - [ ] nada que dê ciência ao cliente (tipping-off)
   - [ ] `@Tag`/`@Operation` (erros padrão já são globais; `@ApiResponse` só para erro específico)
   - [ ] erros via `BusinessException`
3. Teste `@SpringBootTest` + `@WithMockUser`: sucesso, validação 400 e role insuficiente 403. Rode `/rodar-testes <Classe>Test`.
