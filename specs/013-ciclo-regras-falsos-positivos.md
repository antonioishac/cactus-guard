# 013 — Ciclo de Vida de Regras e Gestão de Falsos Positivos

## Ciclo de Vida de Regras
1. **Criação** — definição de cenário, parâmetros e severidade.
2. **Teste / Backtesting** — execução sobre dados históricos para medir volume de alertas e taxa de falsos positivos.
3. **Aprovação** — workflow de aprovação interna.
4. **Ativação** — regra entra em produção (versionada).
5. **Monitoramento** — acompanhamento de volume e qualidade dos alertas.
6. **Ajuste / Desativação** — mudanças sempre versionadas e auditadas.

## Versionamento
- Toda alteração de regra gera nova versão.
- Deve ser possível saber qual versão da regra estava ativa em qualquer data passada.
- Histórico de quem criou/alterou e por quê.

## Gestão de Falsos Positivos
- Analista pode marcar alerta como falso positivo com justificativa.
- Feedback deve poder alimentar calibração futura (manual ou semi-automática).
- Relatórios de taxa de falso positivo por regra e por cenário.
- Possibilidade de criar exceções ou whitelists controladas (com auditoria).

## Boas Práticas
- Evitar regras que gerem volume excessivo sem valor investigativo.
- Priorizar regras que cruzem comportamento com perfil do cliente.
- Documentar a lógica de cada regra para auditoria regulatória.
