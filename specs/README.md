# Specs CactusGuard — Índice

Leia sob demanda: só a spec da tarefa. Em conflito, vale a interpretação regulatória mais conservadora (003) e a decisão mais recente aqui registrada.

## Regulatório (ler antes de mexer em alerta, case, SISCOAF, PEP, retenção)
| Arquivo | Conteúdo |
|---|---|
| 003-requisitos-regulatorios.md | Normas, prazos 45+45+D+1, espécie ≥ 50k, CSNU, tipping-off, retenção 10 anos, declaração negativa |
| 009-cenarios-monitoramento.md | Cenários da Carta Circular 4.001 |
| 017-pep-completo.md | PEP: definição, relacionados, expiração +5 anos, aprovação da alta administração |
| 046-catalogo-campos-pld.md | Campos por entidade: por que importam e qual norma exige |

## Domínio e regras de negócio
| Arquivo | Conteúdo |
|---|---|
| 001-visao-geral.md | Visão, público-alvo, princípios |
| 002-modelo-dados.md | Modelo de dados lógico (detalhe de campos na 046) |
| 004-modulos-funcionais.md | Módulos funcionais |
| 008-regras-de-negocio.md | Risco (ABR), cenários, alertas, decisão de case, UBO |
| 010-kyc-kye-kyp-risco.md | KYC/KYB, KYE, KYP, KYS, classificação de risco, EDD |
| 011-screening-listas.md | Screening, listas, homônimos, CSNU |
| 012-case-management-workflows.md | Case, dossiê, quatro olhos, EDD, declaração negativa |
| 013-ciclo-regras-falsos-positivos.md | Ciclo de vida de regras, backtesting, falsos positivos |
| 014-mvp-escopo-completo.md | Escopo do MVP |
| 016-ingestao-migracao-onboarding.md | Ingestão: migração bulk vs onboarding |
| 018-servico-alertas.md | Tipos de alerta e funcionalidades |
| 019-relatorios-pdf-excel.md | Relatórios PDF/Excel |
| 020-modulo-autenticacao.md | Auth, RBAC, JWT, usuários |
| 033-contratos-api.md | Contratos REST principais |

## Arquitetura e engenharia (backend)
| Arquivo | Conteúdo |
|---|---|
| 047-padroes-tecnicos-backend.md | Java 25 / Spring Boot 4.x, pacotes, hexagonal seletivo, Kafka, multi-tenancy, FTS |
| 005-arquitetura-tecnica.md | Arquitetura técnica geral |
| 006-roadmap.md | Roadmap e fases |
| 007-convencoes-qualidade.md | Convenções de código e critérios de qualidade |
| 015-arquitetura-gaps.md | Decisões iniciais e evoluções pós-MVP |
| 034-plano-testes-performance.md | Performance de buscas/listagens |
| 036-cache-redis.md | Cache Redis (regras e screening) |
| 037-integracoes-externas.md | Provedores de screening, SISCOAF, KYC |
| 038-observabilidade.md | Métricas e tracing |
| 039-rate-limiting.md | Rate limiting |
| 040-criptografia.md | AES-256-GCM, hash de busca, mascaramento, backup |
| 041-testes-carga.md | Testes de carga |
| 042-credenciais-integracoes.md | Obtenção de credenciais de provedores |
| exemplo-fulltext-party.md | Exemplo de full-text search (Party) |
| exemplo-fulltext-alert.md | Exemplo de full-text search (Alert) |

## Frontend
| Arquivo | Conteúdo |
|---|---|
| 021-frontend-web-visao.md | Web: visão e arquitetura |
| 022-frontend-web-auth.md | Web: autenticação |
| 023-frontend-web-alertas.md | Web: alertas |
| 025-frontend-web-cases.md | Web: cases e dossiê |
| 026-frontend-web-kyc-pep.md | Web: KYC, PEP e risco |
| 027-frontend-web-relatorios.md | Web: relatórios |
| 028-frontend-web-usuarios.md | Web: usuários e roles |
| 029-frontend-web-regras.md | Web: regras de monitoramento |
| 030-frontend-web-siscoaf.md | Web: comunicação SISCOAF |
| 031-frontend-web-dashboard.md | Web: dashboard |
| 035-identidade-visual.md | Identidade visual e logo |
| 024-frontend-mobile-visao.md | Mobile (Flutter): visão |
| 032-frontend-mobile-alertas.md | Mobile: alertas |
| ../specs-frontend/043-design-ui-ux.md | Decisão de design UI/UX |
| ../specs-frontend/044-layout-navegacao.md | Layout e navegação |
| ../specs-frontend/045-dashboard.md | Dashboard (implementação) |

Próximo número livre: **048**. Não reutilize números; renumere com `git mv` e atualize referências.

## Status de implementação vs specs

Decisões das specs que **ainda não existem no código** (bloqueiam comercialização):

| Item | Spec | Status |
|---|---|---|
| Multi-tenancy (`tenant_id` + filtro Hibernate + RLS) | 047, 015, 002 | Pendente — nenhuma entidade tem `tenant_id` |
| Eventos de domínio em Kafka (DLQ, retry, reprocesso) | 047 | Pendente — sem Kafka no código |
| Retenção de 10 anos | 003 | **Divergente** — `job/DataRetentionJob` apaga AuditLog/DataAccessLog após 5 anos (deveria ser ≥ 10 e não apagar trilha de auditoria) |
| Decisão `COMMUNICATE \| ARCHIVE` + `/escalate` + `/approve` | 033, 012 | Pendente — código aceita `APPROVED/REJECTED/ESCALATED` |
| Quatro olhos (PEP / alto risco / EDD / ativação de regra) | 012, 017 | Pendente |
| Bloqueio CSNU (Lei 13.810 / Res. BCB 44) | 003, 011 | Pendente |
| Declaração negativa anual | 003, 012 | Pendente |
| Comunicação automática de provisionamento de saque em espécie | 003 | A verificar no motor de regras |
| Backtesting de regras | 013 | Pendente |
| Reconstrução histórica (regras/risco/PEP em data passada) | 002, 008 | Parcial (RiskHistory existe; regras e PEP não) |
| Campos do catálogo 046 | 046 | Em implementação |
| Diagramas de arquitetura (.drawio) | 001 | Não versionados |
