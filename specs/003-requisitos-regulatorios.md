# 003 — Requisitos Regulatórios

## Normas Principais

- Circular BCB nº 3.978/2020 (ABR, controles internos de PLD/FT)
- Carta Circular BCB nº 4.001/2020 (rol de indícios / cenários)
- Lei nº 9.613/1998
- Requisitos de comunicação ao COAF via SISCOAF

## Requisitos Críticos do Sistema

### Prazos (Circular 3.978)
- Seleção de operações/situações suspeitas: até 45 dias da ocorrência.
- Análise formalizada em dossiê: até 45 dias da seleção.
- Comunicação ao COAF: dia útil seguinte à decisão de comunicar.
- Não dar ciência ao envolvido da comunicação.

### Comunicação Automática
- Operações em espécie ≥ R$ 50.000,00 devem ser comunicadas automaticamente.

### Documentação e Auditabilidade
- Parâmetros, variáveis, regras e cenários devem ser documentados e auditáveis.
- Trilha completa de todas as decisões (quem, quando, com base em quê).
- Retenção mínima de 10 anos para KYC, alertas, dossiês e logs relevantes.

### Abordagem Baseada em Risco (ABR)
- Intensidade de controles proporcional ao risco do cliente, produto e canal.
- Classificação de risco obrigatória.
- Diligência reforçada para PEPs e alto risco.

### Outros
- Identificação de Beneficiário Final (participação ≥ 25%).
- Screening de PEPs e listas de sanções na entrada e periodicamente.
- Avaliação Interna de Risco (AIR) deve ser suportada (mesmo que o documento em si seja externo).
- Relatório de efetividade anual deve ser facilitado pelos dados do sistema.

## Comportamento Esperado do Sistema

- O sistema **nunca** deve comunicar automaticamente ao COAF sem decisão humana (exceto comunicações automáticas legais, como espécie ≥ R$ 50k).
- Toda regra deve poder ser justificada e versionada.
- Deve ser possível reconstruir o estado de um cliente e as regras vigentes em qualquer momento passado.
