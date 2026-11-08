# .claude/ — configuração do Claude Code no CactusGuard

Este arquivo é só para humanos: o Claude Code não carrega `.claude/README.md` automaticamente.

```
.claude/
├── settings.json        permissions (dono: trilha de docs) + hooks
├── agents/              subagentes (contexto isolado, tools mínimas)
├── skills/<nome>/SKILL.md   fluxos sob demanda (/nome)
├── rules/               instruções por área, carregadas só quando arquivos casam com `paths`
└── hooks/               scripts dos hooks (bash + Python, LF)
```

## Subagentes (`agents/`)

| Agente | Tools | Model | Quando usar |
|---|---|---|---|
| pld-compliance-reviewer | Read, Grep, Glob, Bash (só leitura) | opus | Revisar mudanças em alertas/cases/regras/COAF/KYC/PII contra 3.978, 4.001 e LGPD |
| fraud-analyst-expert | Read, Grep, Glob | opus | Avaliar se regras/campos/telas cobrem tipologias e red flags |
| security-reviewer | Read, Grep, Glob, Bash (só leitura) | opus | AuthZ por role, IDOR, PII em logs, criptografia, segredos |
| backend-implementer | Read, Grep, Glob, Edit, Write, Bash | sonnet | Implementar no backend Java/Spring e rodar testes do módulo |
| flyway-migration-guardian | Read, Grep, Glob, Write, Bash (sem Edit) | sonnet | Criar/revisar migrations; checar entity↔coluna |
| test-engineer | Read, Grep, Glob, Edit, Write, Bash | sonnet | Escrever/rodar testes de regras e fluxos regulatórios |
| frontend-implementer | Read, Grep, Glob, Edit, Write, Bash | sonnet | Telas React/TS conforme specs 021–031 e 043–045 |
| spec-keeper | Read, Grep, Glob, Edit, Write, Bash | haiku | Sincronizar specs/README e CLAUDE.md com o código |

O Claude delega automaticamente pela `description`; para forçar, peça "use o subagente X".

## Skills (`skills/`)

| Skill | Gatilho |
|---|---|
| `/nova-migration` | qualquer mudança de schema/índice/seed |
| `/novo-modulo-backend` | novo módulo/agregado no backend |
| `/novo-endpoint` | novo endpoint REST em módulo existente |
| `/nova-regra-monitoramento` | nova tipologia ou ajuste de regra (Carta 4.001) |
| `/rodar-testes` | rodar teste único, módulo ou suíte sem mascarar exit code |
| `/revisao-regulatoria` | antes de commit/PR em áreas reguladas (compliance + segurança em paralelo) |
| `/commit-semantico` | só manual: commit Conventional Commits com barreiras |

## Hooks (`hooks/`, registrados em `settings.json`)

| Evento / matcher | Script | Efeito |
|---|---|---|
| SessionStart `startup\|clear\|compact` | `session_context.sh` | 3 linhas: branch, áreas alteradas, próxima migration |
| PreToolUse `Edit\|Write` | `block_secrets.py` | bloqueia `.env*` (exceto `.env.example`), chave privada, `SECRET=valor` |
| PreToolUse `Edit\|Write\|Bash\|PowerShell` | `protect_migrations.py` | bloqueia alterar migration rastreada pelo git (ver abaixo). **Registro pendente**: será incluído no `settings.json` quando terminar a trilha de migrations V15–V20 |
| PostToolUse `Edit\|Write` | `java_check.py` | avisa (sem build) PII em log, `System.out`, controller sem `@PreAuthorize` |

- Contrato: hook PreToolUse que sai com código 2 bloqueia a ação e o stderr vai para o Claude; em PostToolUse o código 2 só avisa.
- `py.sh` escolhe `python3`/`python` e roda `-I -X utf8`. Sem Python o hook não bloqueia nada.
- Exceção de migration (somente migration NUNCA aplicada em nenhum ambiente, com motivo no PR): exporte `CACTUS_ALLOW_MIGRATION_EDIT=V15,V16` (ou `=1`) antes de abrir o Claude.
- Leitura de `target/`, `node_modules/`, `dist/` já é negada por `permissions.deny`, por isso não há hook para isso.
- Scripts precisam de fim de linha LF. Garanta com `.gitattributes`: `*.sh text eol=lf` e `.claude/hooks/* text eol=lf`.
- Testar um hook: `echo '{"tool_name":"Edit","tool_input":{"file_path":"<abs>/db/migration/V5__x.sql"}}' | bash .claude/hooks/py.sh protect_migrations.py; echo $?`

## Rules (`rules/`)

`backend-java.md`, `migrations.md`, `frontend.md`, `regulatorio.md`: cada uma tem frontmatter `paths` e só entra no contexto quando o Claude lê arquivos que casam com esses padrões.

## Como estender

- **Novo agente**: `agents/<nome>.md` com `name`, `description` (diga quando usar e quando NÃO usar), `tools` mínimas e `model`. Revisores não recebem Edit/Write.
- **Nova skill**: `skills/<nome>/SKILL.md` com `description` e passo a passo curto. Use `disable-model-invocation: true` quando o fluxo tiver efeito colateral (commit, deploy).
- **Nova rule**: `rules/<area>.md` com `paths`. Se a regra vale para tudo, ela vai no CLAUDE.md.
- **Novo hook**: script em `hooks/` e registro em `settings.json`. Teste via stdin antes de registrar. Hook roda a cada tool call, então nada de build ou rede.
- **Preferências pessoais**: `.claude/settings.local.json` e `CLAUDE.local.md` (não versionar).
- **MCP (opcional)**: se quiser consultar o Postgres de dev, registre um servidor MCP Postgres só no seu escopo (`claude mcp add --scope local ...`), com usuário de leitura e URL em variável de ambiente. Não versione `.mcp.json` com acesso a banco.

## Economia de tokens

1. **CLAUDE.md enxuto** (< 150 linhas): só comandos, invariantes e onde achar as coisas. Ele é carregado em toda sessão.
2. **Rules por `paths`**: o detalhe de cada área só entra no contexto quando aquela área é tocada.
3. **Skills sob demanda**: só a `description` fica no contexto; o corpo carrega quando a skill é usada.
4. **Subagentes para explorar e revisar**: a leitura de muitos arquivos fica no contexto do subagente, que devolve só o resumo. Modelos mais baratos (`sonnet`/`haiku`) ficam com o trabalho mecânico.
5. **Deny de pastas geradas** (`target/`, `node_modules/`, `dist/`, `.idea/`, `*.log`) em `permissions.deny`.
6. **Saídas curtas**: rode testes com `mvn -q` e leia só o trecho com falha do surefire. Nunca despeje logs inteiros.
7. **`/clear` entre tarefas não relacionadas** e **`/compact`** em tarefas longas. O hook SessionStart reinjeta o contexto mínimo.
