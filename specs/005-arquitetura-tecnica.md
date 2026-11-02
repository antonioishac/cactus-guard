# 005 — Arquitetura Técnica

## Visão Geral

- API-first (REST + eventos).
- Multi-tenant com isolamento forte.
- Camada de ingestão (batch + eventos).
- Motor de regras configurável.
- Banco de dados relacional com suporte a JSONB.

## Componentes Recomendados

- **Backend**: FastAPI (Python) ou NestJS (Node.js) ou Go — a ser definido.
- **Banco de dados**: PostgreSQL (principal), com JSONB.
- **Filas / Eventos**: Kafka, RabbitMQ ou SQS (para near real-time e processamento assíncrono).
- **Motor de regras**: Drools, custom engine ou regras baseadas em JSON/DSL.
- **Autenticação**: JWT + controle de acesso baseado em papéis (RBAC).
- **Observabilidade**: Logs estruturados, métricas, tracing.

## Multi-tenancy

- Shared database com `tenant_id` + Row Level Security (RLS) como padrão.
- Opção de schema por tenant ou private cloud para clientes enterprise.

## Segurança

- Criptografia em trânsito e em repouso.
- Controle de acesso granular.
- Logs imutáveis.
- Segregação de ambientes.
- Alinhamento com requisitos de cibersegurança do Bacen (quando aplicável).

## Ingestão de Dados

1. Ingestão bruta (staging).
2. Normalização e qualidade de dados.
3. Modelo canônico.
4. Publicação para o motor de regras e agregados.

## Critérios de Qualidade

- Toda regra de monitoramento deve ser testável e versionada.
- Toda decisão deve ter justificativa e trilha.
- Performance adequada para volumes relevantes.
- Código limpo, com separação de camadas (domínio, aplicação, infraestrutura).
