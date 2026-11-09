# CI/CD e segurança da cadeia de suprimentos

Spec de referência: `specs/048` (Fase 1, lacuna B4). Arquivos em `.github/`, `.gitleaks.toml`, `SECURITY.md`,
`cactusguard-backend/Dockerfile`.

## Workflows

| Workflow | Gatilho | O que faz | Check(s) |
|---|---|---|---|
| `backend.yml` | push `main` (paths backend), PR, manual | `./mvnw verify`: unit (H2) + IT (Testcontainers PostgreSQL/Kafka), JaCoCo (XML/HTML/CSV + resumo no job), relatório JUnit, SBOM CycloneDX (artefato) | `backend-verify` |
| `web.yml` | push `main` (paths web), PR, manual | `npm ci --ignore-scripts`, `npm audit`, typecheck, lint, test, build, SBOM CycloneDX (prod) | `web-checks` |
| `mobile.yml` | push `main` (paths mobile), PR, manual | `pub get --enforce-lockfile`, analyze, test, `build web` (mock) | `mobile-checks` |
| `security.yml` | push `main`, PR, semanal, manual | CodeQL (java-kotlin build manual, javascript-typescript, actions; `security-extended`), gitleaks (PR: commits do PR; senão histórico inteiro), Trivy fs (vuln + misconfig, SARIF no Security tab + gate), dependency review (só PR) | `codeql (java-kotlin)`, `codeql (javascript-typescript)`, `codeql (actions)`, `gitleaks`, `trivy-fs`, `dependency-review` |
| `docker.yml` | push `main` (paths backend), tag `v*`, PR, manual | build multi-stage, Trivy image (SARIF + gate), SBOM da imagem (CycloneDX); em `main`/tag: push no GHCR **do mesmo tar escaneado** + atestação de proveniência SLSA (Sigstore) | `docker-build-scan` (`docker-push` não é check de PR) |
| `_changes.yml` | `workflow_call` | detecta se o PR tocou o projeto | — |

**Por que os PRs não usam `on.pull_request.paths`**: workflow pulado por filtro de caminho deixa o check obrigatório
“pendente” para sempre e trava o merge. Em PR, cada workflow roda o job `changes` (git diff) e os jobs pesados ficam
`skipped` — que conta como sucesso — quando o projeto não mudou. Em `push` o filtro `paths` é nativo.

Padrões em todos os workflows: `permissions: {}` no topo e mínimo por job; actions fixadas por **SHA de commit**
(comentário `# vX.Y.Z`; Dependabot atualiza); imagens de ferramenta (gitleaks, Trivy) e bases do Dockerfile fixadas
por **digest**; `persist-credentials: false`; `concurrency` cancela execução antiga do mesmo PR; caches de Maven,
npm e pub. Único job com escrita em registry: `docker-push` (`packages: write`, via `GITHUB_TOKEN`, environment
`registry`). Nenhum segredo manual é necessário.

## Políticas de bloqueio

| Controle | Bloqueia | Não bloqueia (visível) |
|---|---|---|
| Testes/lint/typecheck/analyze | qualquer falha | — |
| Cobertura JaCoCo | — (por ora) | meta inicial **linhas ≥ 80%, branches ≥ 65%** (`check` com `haltOnFailure=false`). Medido em 2026-11-08: 84,9% / 69,9%. Ligar `haltOnFailure=true` após algumas semanas estável. |
| `npm audit` | high/critical (prod e dev) | moderate/low (listados no log) |
| Trivy fs / image | HIGH/CRITICAL **com correção disponível** (`--ignore-unfixed`) | demais severidades (SARIF no Security tab) |
| Dependency review (PR) | dependência nova/alterada com advisory HIGH+; licença copyleft forte (GPL, AGPL, SSPL, EUPL, OSL, CPAL) ou não comercial (BUSL, CC-NC) — ver `.github/dependency-review-config.yml` | copyleft fraco (LGPL, MPL, EPL, CDDL) |
| gitleaks | qualquer segredo fora da allowlist | — |

Exceção (vulnerabilidade sem correção aplicável, falso positivo, licença): registrar no PR com justificativa,
dono e data de revisão; nunca allowlist genérica. Dependências GPL com *Classpath Exception* (ex.: algumas libs
Jakarta/JDK) podem exigir exceção explícita por PURL.

**gitleaks**: allowlist só para credenciais de dev/teste versionadas de propósito (`application.yml` →
`app_password`; `application-dev.yml`; `db/dev/R__dev_users.sql`; `application-test.yml`), por arquivo **e** valor
exato, com `targetRules` (allowlist global com `paths` faria o gitleaks pular o arquivo inteiro). Há uma entrada de
**histórico** (default público de `JWT_SECRET` no commit inicial) — ver comentário no `.gitleaks.toml`.

## Rodar localmente (git bash no Windows)

```bash
cd /c/Projetos/system-projects/cactus-guard
R=$(cygpath -w "$PWD")

# Lint dos workflows (inclui shellcheck)
MSYS_NO_PATHCONV=1 docker run --rm -v "$R:/repo" -w /repo rhysd/actionlint:latest -no-color

# Backend (igual ao CI)
(cd cactusguard-backend && JAVA_HOME=/c/Java/jdk-25.0.2 ./mvnw -B -ntp verify; echo EXIT=$?)
#   cobertura: cactusguard-backend/target/site/jacoco/index.html
#   SBOM:      cactusguard-backend/target/classes/META-INF/sbom/application.cdx.json

# Web
(cd cactusguard-web && npm ci --ignore-scripts && npm audit --audit-level=high && npm run typecheck \
  && npm run lint && npm test -- --run && npm run build \
  && npx --yes @cyclonedx/cyclonedx-npm@6.0.1 --omit dev --output-file sbom-web.cdx.json)

# Mobile
(cd cactusguard-mobile && flutter pub get --enforce-lockfile && flutter analyze && flutter test \
  && flutter build web --release --dart-define=USE_MOCK=true)

# Segredos (histórico)
MSYS_NO_PATHCONV=1 docker run --rm -v "$R:/repo:ro" zricethezav/gitleaks:v8.30.1 git /repo -c /repo/.gitleaks.toml

# Trivy fs — monte ~/.m2 e use --offline-scan: sem isso o Trivy busca cada POM no Maven Central e
# toma HTTP 429 (IP bloqueado por 30 min). Rode antes o mvn verify/package para popular o ~/.m2.
M2=$(cygpath -w "$HOME/.m2")
MSYS_NO_PATHCONV=1 docker run --rm -v "$R:/repo:ro" -v "$M2:/root/.m2:ro" aquasec/trivy:0.76.2 \
  fs --offline-scan --config /repo/.github/trivy.yaml --scanners vuln,misconfig /repo

# Imagem + scan
docker build -t cactusguard-backend:ci cactusguard-backend
MSYS_NO_PATHCONV=1 docker run --rm -v //var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.76.2 \
  image --scanners vuln,secret cactusguard-backend:ci
```

## Proteção de branch recomendada para `main`

Configurar em *Settings → Rules → Rulesets* (ou *Branches → Branch protection rules*). Não é versionável neste repo.

- **Require a pull request before merging**: 1 aprovação; *Dismiss stale approvals*; *Require review from Code Owners*;
  *Require approval of the most recent push* (quem fez o último push não aprova sozinho);
  *Require conversation resolution*.
- **Require status checks to pass** + *Require branches to be up to date*. Marcar exatamente:
  `backend-verify`, `web-checks`, `mobile-checks`, `docker-build-scan`, `codeql (java-kotlin)`,
  `codeql (javascript-typescript)`, `codeql (actions)`, `gitleaks`, `trivy-fs`, `dependency-review`.
  (Os nomes só aparecem para seleção depois da primeira execução de cada workflow.) Opcional: regra de
  *Code scanning results* bloqueando alertas `high`+ do CodeQL/Trivy.
- **Block force pushes** e **Restrict deletions**; sem bypass (inclusive admin) — *Do not allow bypassing*.
- **Require linear history** (squash ou rebase merge).
- **Require signed commits** (GPG/SSH/S/MIME; commits pelo GitHub UI já são assinados).
- Push direto em `main`: bloqueado pelas regras acima (todo change via PR).
- Tags `v*`: ruleset de tag restringindo criação/atualização/remoção a mantenedores.
- *Environment* `registry`: restringir a `main` e tags `v*` (*Deployment branches and tags*); opcional exigir revisor.

Também em *Settings → Code security*: Dependency graph, Dependabot alerts e security updates, Secret scanning +
**Push protection**, Private vulnerability reporting, Code scanning (CodeQL via este workflow — **não** habilitar o
“default setup”, que conflita com o advanced setup do `security.yml`). Em *Actions → General*: *Workflow permissions*
= read-only por padrão; exigir aprovação para workflows de forks; opcional *Require actions to be pinned to a full-length commit SHA*.

## Mapeamento para due diligence (o que existe hoje, sem exagero)

| Requisito | Evidência neste repositório | Lacuna conhecida |
|---|---|---|
| Res. CMN 4.893/2021 — política de segurança cibernética do contratante e avaliação de fornecedor de processamento/nuvem (art. 3º; arts. 11–17) | Controles de desenvolvimento seguro automatizados (SAST, segredos, SCA, SBOM, scan de imagem), proveniência de artefato, `SECURITY.md` com divulgação coordenada | Não substitui: política formal de segurança cibernética, plano de resposta a incidentes, relatório anual, DR/continuidade, pentest independente, contrato com cláusulas do art. 17 |
| ISO/IEC 27001:2022 A.8.25 Ciclo de vida de desenvolvimento seguro | PR obrigatório, checks bloqueantes, template com checklist regulatório | SDLC documentado formalmente (política) |
| A.8.26 Requisitos de segurança de aplicação | Invariantes no `CLAUDE.md`/specs + checklist do PR | Threat modeling formal por feature |
| A.8.27 Arquitetura e princípios de engenharia segura | Menor privilégio no CI, imagem não-root, base por digest, segredos fora da imagem | — |
| A.8.28 Codificação segura | CodeQL `security-extended`, lint, revisão por Code Owners | Treinamento de codificação segura registrado |
| A.8.29 Testes de segurança em desenvolvimento e aceitação | CodeQL, Trivy, gitleaks, dependency review, testes de isolamento de tenant/regras | **DAST** e pentest ainda inexistentes (Fase 5) |
| A.8.30 Desenvolvimento terceirizado | Dependency review (licença/vulnerabilidade), Dependabot com cooldown, SBOM | Avaliação de fornecedores de dados/provedores |
| A.8.31 Separação de ambientes | Perfis `dev`/`test` com credenciais públicas apenas locais; prod só via variáveis/cofre | Ambientes de homologação/produção ainda não provisionados |
| A.8.32 Gestão de mudanças | Proteção de branch, histórico linear, commits assinados, proveniência de imagem | Registro de aprovação de mudança em produção (CAB) |

Nada aqui equivale a certificação ISO 27001 ou SOC 2 — são controles técnicos que servem de evidência quando
esses programas forem iniciados (Fase 5 da spec 048).
