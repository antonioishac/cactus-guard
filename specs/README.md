# Specs da Plataforma PLD/FT — MVP Completo

## Ordem de Leitura Recomendada

| Arquivo | Conteúdo |
|---------|----------|
| 001-visao-geral.md | Visão e princípios |
| 002-modelo-dados.md | Modelo de dados completo (revisado) |
| 003-requisitos-regulatorios.md | Requisitos legais |
| 016-ingestao-migracao-onboarding.md | Arquitetura de ingestão (migrados vs novos) |
| 017-pep-completo.md | Tratamento completo de PEPs |
| 018-servico-alertas.md | Serviço de alertas abrangente |
| 019-relatorios-pdf-excel.md | Relatórios com PDF e Excel |
| 009-cenarios-monitoramento.md | Cenários Carta 4.001 |
| 010-kyc-kye-kyp-risco.md | KYC, KYE, risco e EDD |
| 011-screening-listas.md | Screening |
| 012-case-management-workflows.md | Case e workflows |
| 013-ciclo-regras-falsos-positivos.md | Regras e falsos positivos |
| 014-mvp-escopo-completo.md | Escopo final do MVP |
| 005-arquitetura-tecnica.md | Arquitetura |
| 015-arquitetura-gaps.md | Evoluções futuras |
| 007-convencoes-qualidade.md | Convenções |
| 007-padroes-tecnicos-backend.md | Padrões técnicos e arquitetura do backend (CactusGuard) |
| 020-modulo-autenticacao.md | Módulo de autenticação, autorização e gestão de usuários |
| 021-frontend-web-visao.md | Frontend Web: visão geral e arquitetura |
| 022-frontend-web-auth.md | Frontend Web: autenticação e segurança |
| 023-frontend-web-alertas.md | Frontend Web: módulo de alertas |
| 024-frontend-mobile-visao.md | Frontend Mobile (Flutter): visão e escopo |
| 025-frontend-web-cases.md | Frontend Web: Cases e Dossiê |
| 026-frontend-web-kyc-pep.md | Frontend Web: KYC, PEP e Risco |
| 027-frontend-web-relatorios.md | Frontend Web: Relatórios e Exportação |
| 028-frontend-web-usuarios.md | Frontend Web: Gestão de Usuários e Roles |
| 029-frontend-web-regras.md | Frontend Web: Monitoramento e Regras |
| 030-frontend-web-siscoaf.md | Frontend Web: Comunicação SISCOAF |
| 031-frontend-web-dashboard.md | Frontend Web: Dashboard |
| 032-frontend-mobile-alertas.md | Frontend Mobile: Alertas e Notificações |
| 033-contratos-api.md | Contratos de API (principais endpoints) |
| exemplo-fulltext-party.md | Exemplo Full-Text Search — Party |
| exemplo-fulltext-alert.md | Exemplo Full-Text Search — Alert |
| 034-plano-testes-performance.md | Plano de testes de performance (buscas e listagens) |
| 035-identidade-visual.md | Identidade visual e logo oficial do CactusGuard |
| 036-cache-redis.md | Cache distribuído com Redis para regras e screening |
| 037-integracoes-externas.md | Integrações com provedores de screening, SISCOAF e KYC |
| 038-observabilidade.md | Métricas e tracing |
| 039-rate-limiting.md | Rate limiting por endpoint |
| 040-criptografia.md | Criptografia de dados sensíveis |
| 041-testes-carga.md | Testes de carga e critérios de performance |
| 042-credenciais-integracoes.md | Processo para obter credenciais dos provedores externos |

Coloque esta pasta `specs/` na raiz do repositório junto com o `CLAUDE.md`.  
Diagrama principal: `PLD_Arquitetura_Completa_MVP.drawio`