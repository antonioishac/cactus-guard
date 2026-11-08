# 012 — Case Management, EDD e Workflows

## Fluxo Regulatório Obrigatório (Circular 3.978)
1. Ocorrência / transação.
2. Seleção para análise (até 45 dias da ocorrência).
3. Análise formalizada em dossiê (até 45 dias da seleção).
4. Decisão: **Comunicar** ou **Arquivar** (justificativa obrigatória). Escalar **não** é decisão final — é transição de workflow.
5. Se comunicar: envio ao COAF até o dia útil seguinte à decisão.
6. Não dar ciência ao envolvido (Lei 9.613/1998, art. 11, II) — ver 003.

## Quatro olhos (aprovação de nível superior)
- Obrigatória quando o case envolve PEP (titular ou relacionado), cliente de alto risco ou EDD.
- Quem decide (analista) ≠ quem aprova (supervisor/alta administração). O sistema bloqueia aprovação pelo próprio autor.
- Estados: `IN_ANALYSIS` → `PENDING_APPROVAL` (decisão proposta) → `CLOSED` (aprovada) ou volta a `IN_ANALYSIS` (rejeitada, com justificativa).
- Para início/manutenção de relacionamento com PEP, a aprovação é da alta administração (ver 017).
- O prazo de 45 dias da análise continua correndo durante a aprovação.

## Case Management
- Atribuição automática ou manual de casos.
- Escalação por severidade, valor ou condição de PEP.
- Anexação de evidências e documentos.
- Comentários e histórico de ações.
- SLA visual e alertas de vencimento.
- Possibilidade de análise em lote (para casos similares).

## Dossiê
Deve consolidar:
- Dados cadastrais e de risco atuais e históricos.
- Transações relevantes.
- Hits de screening.
- Justificativa da decisão.
- Evidências anexadas.
- Trilha de quem analisou e quando.

## EDD Workflow
- Gatilhos automáticos ou manuais.
- Aprovação de nível superior obrigatória em casos de alto risco / PEP.
- Documentação reforçada de origem dos recursos e propósito.
- Revisão periódica mais frequente.

## Declaração Negativa
- Suporte à geração e controle da declaração anual de não ocorrência (quando não houver comunicações no ano).
- Prazo: até 10 dias úteis após o encerramento do ano civil (Circular 3.978) — **a validar com jurídico** (artigo e prazo vigente). Ver 003.

## Comunicações Automáticas
- Espécie ≥ R$ 50.000,00 (depósito, aporte, saque e **pedido de provisionamento de saque**): fluxo automático para SISCOAF (sem análise de suspeita).
- Outras comunicações automáticas conforme normas vigentes (ex.: ativos virtuais, se aplicável).
