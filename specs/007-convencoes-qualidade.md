# 007 — Convenções e Critérios de Qualidade

## Convenções de Código

- Tipagem forte.
- Testes unitários e de integração obrigatórios para regras de monitoramento e fluxos regulatórios.
- Documentação de APIs via OpenAPI.
- Migrations versionadas.
- Código limpo, com separação clara de camadas (domínio, aplicação, infraestrutura).
- Commits semânticos.

## Critérios de Qualidade

- Toda regra de monitoramento deve ser testável e versionada.
- Toda decisão (arquivar ou comunicar) deve ter justificativa e trilha.
- O sistema deve permitir reconstruir o estado de um cliente e as regras vigentes em qualquer momento passado.
- Falsos positivos devem ser gerenciáveis.
- Performance: o motor de regras deve processar volumes relevantes de transações em janela aceitável.
- Auditabilidade total.

## Instruções para o Agente

Ao implementar qualquer parte deste sistema:

1. Sempre considere a aderência regulatória e a auditabilidade.
2. Em caso de dúvida sobre um requisito normativo, priorize a interpretação mais conservadora e documente a decisão.
3. Consulte as specs na pasta `specs/` antes de implementar uma funcionalidade.
4. Mantenha o modelo de dados consistente com a spec `002-modelo-dados.md`.
5. Não implemente comunicação automática ao COAF sem decisão humana (exceto casos legais de comunicação automática).
