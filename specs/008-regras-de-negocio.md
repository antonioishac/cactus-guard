# 008 — Regras de Negócio

## 1. Classificação de Risco (ABR)

### Regras gerais
- Todo cliente (Party) deve ter uma classificação de risco (baixo / médio / alto) antes do início do relacionamento.
- A classificação deve ser recalculada:
  - Periodicamente (ex.: a cada 12 meses para baixo risco, 6 meses para médio, 3 meses para alto).
  - Por eventos (hit em lista, mudança cadastral relevante, alerta confirmado, etc.).
- A intensidade do monitoramento e da diligência deve ser proporcional à categoria de risco.
- PEPs e clientes com hit confirmado em listas de sanções devem receber classificação mínima de alto risco (ou diligência reforçada).

### Fatores típicos de score
- Condição de PEP.
- Hit em listas de sanções ou mídia adversa.
- Atividade econômica de alto risco.
- Jurisdição de alto risco (cliente, contraparte ou beneficiário final).
- Estrutura societária complexa ou beneficiário final não identificado.
- Incompatibilidade entre renda/faturamento declarado e movimentação observada.
- Histórico de alertas confirmados.

## 2. Monitoramento Transacional — Cenários Principais

Os cenários abaixo estão alinhados à Carta Circular BCB 4.001/2020 e devem ser implementados de forma parametrizável.

### 2.1 Fracionamento / Estruturação
- Múltiplas transações em período curto (ex.: 24h, 7 dias) cuja soma ultrapasse um limiar, mas cada uma individualmente fique abaixo dele.
- Limiares e janelas devem ser configuráveis por tenant e por categoria de risco.

### 2.2 Movimentação Incompatível com Perfil
- Volume movimentado em período (30/60/90 dias) significativamente superior à capacidade financeira declarada ou ao baseline histórico do cliente.
- Exemplo de regra: volume > X vezes a renda/faturamento declarado ou > Y vezes a média histórica.

### 2.3 Contas-Passagem / Pass-Through
- Entrada de recursos seguida de saída em prazo muito curto (ex.: < 24h ou < 48h) sem lastro econômico aparente.
- Volume de saída próximo do volume de entrada.

### 2.4 Operações com PEPs
- Transações envolvendo PEPs acima de limiares específicos (mais baixos que para clientes comuns).
- Qualquer operação relevante com PEP deve gerar alerta ou revisão.

### 2.5 Jurisdições de Alto Risco
- Operações com contrapartes ou beneficiários em países com deficiências estratégicas segundo o GAFI.
- Exige diligência reforçada.

### 2.6 Valores Arredondados ou Próximos de Limiares
- Transações com valores redondos (ex.: múltiplos de 1.000 ou 10.000) ou ligeiramente abaixo de limiares de comunicação automática.

### 2.7 Espécie (Comunicação Automática)
- Depósitos, aportes, saques e **pedidos de provisionamento de saque** em espécie ≥ R$ 50.000,00 devem gerar comunicação automática ao COAF (não dependem de análise de suspeita). Ver 003.

### 2.8 Outros cenários relevantes
- Múltiplas contas do mesmo cliente com movimentação fragmentada.
- Operações sem fundamento econômico ou legal aparente.
- Alterações cadastrais relevantes seguidas de movimentação atípica.
- Operações com contrapartes em listas restritivas.

## 3. Geração e Tratamento de Alertas

- Uma regra pode gerar um ou mais alertas.
- Alertas devem ter severidade (baixa, média, alta, crítica).
- Alertas de alta severidade ou envolvendo PEPs devem ter SLA mais curto.
- O sistema deve evitar alertas duplicados para o mesmo fato em janela curta (deduplicação configurável).

## 4. Case Management e Decisão

### Fluxo obrigatório
1. Alerta é gerado.
2. Seleção para análise (dentro de 45 dias).
3. Análise formalizada em dossiê (dentro de mais 45 dias).
4. Decisão: **Comunicar** ou **Arquivar**.
5. Se comunicar: geração da comunicação ao COAF no dia útil seguinte.
6. Registro completo da justificativa e evidências.

### Regras de decisão
- A decisão final é sempre humana.
- Toda decisão deve ter justificativa textual obrigatória.
- É possível escalar o caso para nível superior — escalação é transição de workflow, não decisão final.
- Casos com PEP, alto risco ou EDD exigem aprovação de nível superior por usuário distinto do analista (quatro olhos; ver 012).
- Casos arquivados devem permanecer rastreáveis.

## 5. Screening

- Screening deve ocorrer:
  - Na entrada do cliente.
  - Periodicamente (frequência proporcional ao risco).
  - Em eventos (atualização cadastral, nova operação relevante).
- Hit potencial deve gerar alerta ou tarefa de revisão.
- Homônimos devem ser gerenciáveis (falso positivo documentado).

## 6. Beneficiário Final (UBO)

- Para pessoas jurídicas, deve ser identificado o beneficiário final com participação ≥ 25% (direta ou indireta); a instituição pode adotar percentual menor (configurável por tenant).
- Quando não for possível identificar, o cliente deve receber classificação de risco elevada e diligência reforçada.

## 7. Regras de Integridade e Auditoria

- Nenhuma regra de monitoramento pode ser alterada sem versionamento.
- Toda alteração de regra, classificação de risco ou decisão de case deve gerar registro no AuditLog.
- O sistema deve permitir reconstruir, em qualquer data passada, quais regras estavam ativas e qual era a classificação de risco de um cliente.

## 8. Configurabilidade

- Limiares, janelas de tempo, multiplicadores e severidades devem ser configuráveis por tenant.
- O cliente (instituição) deve poder ativar/desativar cenários e ajustar parâmetros dentro de limites seguros definidos pelo sistema.
- Mudanças de configuração devem ser auditadas.
