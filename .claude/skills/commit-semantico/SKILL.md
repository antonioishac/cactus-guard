---
name: commit-semantico
description: Prepara um commit no padrão Conventional Commits em português para o CactusGuard, com escopo por módulo, conferindo antes que não há segredo, PII, artefato gerado ou migration aplicada alterada no stage.
argument-hint: "[mensagem ou escopo opcional]"
disable-model-invocation: true
---

Contexto extra: $ARGUMENTS

1. `git status --porcelain` e `git diff --cached --stat` (se nada staged, proponha o que adicionar — por caminho, nunca `git add -A`).
2. Barreiras (pare e avise se falhar):
   - [ ] nada de `target/`, `node_modules/`, `dist/`, `.idea/`, `.env*` (exceto `.env.example`), `settings.local.json`
   - [ ] nenhuma migration já commitada modificada: `git diff --cached --name-status -- '*db/migration/*'` só pode ter `A`
   - [ ] sem segredo/PII real no diff (`git diff --cached | grep -nE "BEGIN .*PRIVATE KEY|JWT_SECRET=|[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2}"`)
3. Mensagem: `<tipo>(<escopo>): <resumo no imperativo, ≤ 72 chars>`
   - tipos: feat, fix, refactor, test, docs, chore, perf, build, ci, security
   - escopo = módulo (`alert`, `case`, `rule`, `siscoaf`, `kyc`, `party`, `audit`, `auth`, `db`, `web`, `specs`, `claude`)
   - corpo: o porquê + referência regulatória/spec quando houver (ex.: `Ref: Carta 4.001 art. 1º, I; spec 009`)
   - quebra de contrato: rodapé `BREAKING CHANGE:`
4. Mostre a mensagem e os arquivos e só execute `git commit` após confirmação do usuário. Inclua as linhas de atribuição exigidas pelo ambiente.
