# 016 — Arquitetura de Ingestão: Migrados vs Onboarding

## Objetivo
Preparar o sistema para receber grandes volumes de dados de clientes migrados (legado) e novos clientes em onboarding contínuo, com pipelines separados e auditáveis.

## Pipeline de Migração (Bulk)
- Staging Area (tabelas temporárias ou schema staging).
- Validação de qualidade de dados (CPF/CNPJ, campos obrigatórios, consistência).
- Normalização para modelo canônico.
- Screening em lote e classificação de risco em batch.
- Flag `source_type = 'migrated'` e `is_migrated = true`.
- Geração de DataImportBatch com estatísticas e erros.
- Possibilidade de reprocessamento controlado.
- Após validação, promoção para tabelas principais.
- Alertas de qualidade de dados gerados automaticamente.

## Pipeline de Onboarding (Novos)
- Via API ou eventos.
- Processamento síncrono ou near real-time.
- KYC + Screening + Classificação de risco imediata.
- Flag `source_type = 'onboarded'`.
- Bloqueio de ativação se KYC incompleto ou hit crítico.
- Integração com provedores externos em tempo real.

## Separação Arquitetural
- Filas e workers separados.
- Configurações de rate limit e prioridade diferentes.
- Logs e métricas separadas.
- Auditoria de origem de cada registro.

## Escalabilidade
- Processamento paralelo.
- Particionamento.
- Suporte a arquivos grandes (CSV, Parquet) e APIs bulk.
- Monitoramento de progresso e falhas.