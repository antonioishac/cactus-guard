# 003 — Requisitos Regulatórios

> Itens marcados **[validar jurídico]** devem ser confirmados por jurídico/compliance antes de virar material comercial ou regra de produto travada.

## Normas Principais

- Circular BCB nº 3.978/2020 (ABR, controles internos de PLD/FT)
- Carta Circular BCB nº 4.001/2020 (rol de indícios / cenários)
- Lei nº 9.613/1998 (lavagem de dinheiro; deveres de registro e comunicação)
- Lei nº 13.810/2019 + Resolução BCB nº 44/2020 (cumprimento de sanções do CSNU — indisponibilidade de ativos)
- Requisitos de comunicação ao COAF via SISCOAF

## Requisitos Críticos do Sistema

### Prazos (Circular 3.978)
- Seleção de operações/situações suspeitas: até 45 dias da ocorrência.
- Análise formalizada em dossiê: até 45 dias da seleção.
- Comunicação ao COAF: até o dia útil seguinte à decisão de comunicar.
- O sistema deve guardar `occurred_at`, `selected_at`, `analysis_deadline` e `decided_at` para provar o cumprimento (ver 046).

### Vedação de dar ciência (tipping-off)
- Proibido dar ciência ao cliente/envolvido da comunicação ao COAF (Lei 9.613/1998, art. 11, II).
- Reflexo no produto:
  - Comunicações, cases e alertas **nunca** aparecem em canais voltados ao cliente (APIs públicas, extratos, notificações, e-mails ao cliente).
  - Permissões `case:read`/`siscoaf:read` restritas a perfis de PLD/compliance/auditoria; perfis de atendimento/comercial não veem existência de case nem comunicação.
  - Mensagens de bloqueio/recusa exibidas ao cliente usam texto neutro, sem citar PLD/COAF.
  - Exportações e relatórios com dados de comunicação exigem permissão específica e geram registro de acesso.

### Comunicação Automática (espécie)
- Operações em espécie ≥ R$ 50.000,00 são comunicadas ao COAF independentemente de análise de suspeita: **depósito, aporte, saque e pedido de provisionamento de saque** (Circular 3.978).
- O pedido de provisionamento gera comunicação mesmo que o saque não se concretize.

### Declaração Negativa
- Se não houver comunicações no ano civil, a instituição presta declaração de não ocorrência ao COAF via SISCOAF.
- Prazo: até 10 dias úteis após o encerramento do ano civil (Circular 3.978) **[validar jurídico — artigo e prazo vigente]**.
- O sistema deve controlar o prazo, gerar alerta de SLA e registrar o protocolo.

### Sanções do CSNU (Lei 13.810/2019 e Resolução BCB 44/2020)
- Cumprimento **imediato e sem prévio aviso** das resoluções do Conselho de Segurança da ONU: indisponibilidade de ativos de pessoas/entidades sancionadas.
- Comunicar a indisponibilidade ao BCB, ao Ministério da Justiça e Segurança Pública e ao COAF **[validar jurídico — canal, formato e prazo de cada comunicação]**.
- Reflexo no produto:
  - Ingestão da lista consolidada do CSNU com atualização frequente (no mínimo diária) e rescreening da base a cada atualização.
  - Hit confirmado em CSNU dispara bloqueio (indisponibilidade) da Party e alerta crítico; não depende de janela 45+45.
  - Registro da data/hora da publicação da sanção, do hit, do bloqueio e de cada comunicação (prova de "imediato").
  - Desbloqueio somente por exclusão da lista ou ordem competente, com aprovação de nível superior e auditoria.

### Documentação e Auditabilidade
- Parâmetros, variáveis, regras e cenários devem ser documentados e auditáveis.
- Trilha completa de todas as decisões (quem, quando, com base em quê).
- AuditLog append-only (sem UPDATE/DELETE).

### Retenção
- **10 anos** para KYC, alertas, dossiês, comunicações, logs relevantes e backups — Circular BCB 3.978/2020, art. 67 **[validar jurídico — termo inicial de contagem por tipo de registro]**.
- A Lei 9.613/1998 (art. 10, §2º) exige no mínimo 5 anos; adotamos o prazo mais conservador (10 anos) para todos os registros de PLD.

### Abordagem Baseada em Risco (ABR)
- Intensidade de controles proporcional ao risco do cliente, produto e canal.
- Classificação de risco obrigatória.
- Diligência reforçada para PEPs e alto risco.

### Aprovação de nível superior (quatro olhos)
- Relacionamento com PEP: aprovação da alta administração para iniciar/manter (ver 017).
- Cases de PEP, alto risco ou EDD: decisão do analista + aprovação por usuário distinto (ver 012).

### Outros
- Identificação de Beneficiário Final (participação ≥ 25%, direta ou indireta; a instituição pode adotar percentual menor).
- Screening de PEPs e listas de sanções na entrada e periodicamente.
- Avaliação Interna de Risco (AIR) deve ser suportada (mesmo que o documento em si seja externo).
- Relatório de efetividade anual deve ser facilitado pelos dados do sistema.

## Comportamento Esperado do Sistema

- O sistema **nunca** deve comunicar automaticamente ao COAF sem decisão humana (exceto comunicações automáticas legais, como espécie ≥ R$ 50k e as decorrentes da Lei 13.810).
- Toda regra deve poder ser justificada e versionada.
- Deve ser possível reconstruir o estado de um cliente e as regras vigentes em qualquer momento passado.
