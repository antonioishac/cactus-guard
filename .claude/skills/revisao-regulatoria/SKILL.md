---
name: revisao-regulatoria
description: Revisão regulatória e de segurança do diff atual do CactusGuard, orquestrando em paralelo os subagentes pld-compliance-reviewer e security-reviewer e consolidando um veredito único. Use antes de commit/PR que toque alertas, cases, regras, SISCOAF, KYC/PEP, PII, autenticação ou auditoria.
argument-hint: "[ref base, padrão HEAD] [caminhos]"
---

Escopo: $ARGUMENTS (padrão: `git diff HEAD` + arquivos não rastreados relevantes).

1. Levante o escopo uma vez: `git status --porcelain` e `git diff HEAD --stat`. Ignore `target/`, `node_modules/`, `.idea/`.
2. Lance EM PARALELO (mesma mensagem), passando a lista de arquivos e a ref base:
   - `pld-compliance-reviewer`: invariantes PLD/FT e LGPD, specs relevantes.
   - `security-reviewer`: authz, IDOR, PII, criptografia, segredos.
   - Se o diff incluir regras de monitoramento ou campos de transação/cadastro, inclua também `fraud-analyst-expert`.
   - Se incluir `db/migration`, inclua `flyway-migration-guardian` (só revisão).
3. Consolide sem repetir achados:
```
VEREDITO: APROVADO | APROVADO COM RESSALVAS | REPROVADO
BLOQUEANTES (regulatório/segurança): ...
RESSALVAS: ...
LACUNAS DE TESTE: ...
```
4. Não corrija nada automaticamente; pergunte se deve aplicar as correções.
