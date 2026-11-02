# 018 — Serviço de Alertas Completo

## Objetivo
Fornecer um serviço de alertas abrangente para analistas de compliance, cobrindo não apenas alertas transacionais, mas todos os eventos relevantes do sistema.

## Tipos de Alertas
1. **Transacionais**: gerados pelo motor de regras (Carta 4.001).
2. **PEP**: novo hit, mudança de status, relacionamento detectado, vencimento de 5 anos.
3. **Screening**: hits em sanções, mídia adversa, listas internas (cliente ou contraparte).
4. **SLA**: aproximação ou vencimento de prazos (45+45).
5. **Qualidade de dados**: inconsistências em migração, cadastro incompleto, UBO não identificado.
6. **Sistema**: falhas de integração, regras desativadas, volume anômalo de alertas.
7. **EDD / Risco**: recálculo de risco para alto, necessidade de revisão periódica.

## Funcionalidades
- Atribuição automática ou manual.
- Severidade configurável.
- Notificações in-app, email e (opcional) webhook.
- Dashboard de alertas com filtros e priorização.
- Marcação de falso positivo com justificativa.
- Histórico e auditoria.
- Agrupamento e deduplicação.
- SLA visual por alerta.

## Integração
- Geração a partir de eventos do sistema.
- API para consumo e atualização de status.
- Relatórios de alertas exportáveis.