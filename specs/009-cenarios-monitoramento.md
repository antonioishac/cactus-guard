# 009 — Cenários de Monitoramento Transacional (Detalhados)

## Princípios
- Cenários alinhados à Carta Circular BCB 4.001/2020 (art. 1º e seus grupos).
- Todos os parâmetros (limiares, janelas, multiplicadores) devem ser configuráveis por tenant e por categoria de risco.
- Regras devem ser versionadas e testáveis.
- Preferir cruzamento com perfil do cliente (capacidade, atividade, baseline histórico) em vez de regras puramente absolutas.

## Grupos Principais da Carta 4.001

### I - Operações em espécie (moeda nacional)
- Fragmentação de depósitos/saques para evitar limiares.
- Valores incompatíveis com atividade ou capacidade financeira.
- Aumentos súbitos seguidos de transferências rápidas.
- Comunicação automática: espécie ≥ R$ 50.000,00 (depósito, aporte, saque e pedido de provisionamento de saque) — ver 003.

### II - Moeda estrangeira em espécie e cartões pré-pagos
- Movimentações atípicas ou incompatíveis.
- Carga/recarga seguida de saque imediato.

### III - Identificação e qualificação de clientes
- Resistência ou informação falsa no cadastro.
- Cadastro desatualizado com movimentação relevante.
- Beneficiário final não identificável.

### IV - Movimentação de contas de depósito e pagamento
- Volume incompatível com perfil.
- Valores arredondados ou pouco abaixo de limiares.
- Múltiplas contas do mesmo cliente com fragmentação.
- Contas-passagem (entrada e saída rápida).

### V - Operações de investimento
- Incompatibilidade com capacidade.
- Liquidação atípica.

### VI - Operações de crédito
- Liquidação com recursos incompatíveis.
- Crédito seguido de remessa ao exterior sem fundamento.
- Liquidação antecipada ou por terceiros sem justificativa.

### VII a XVII (outros)
- Recursos de contratos com setor público.
- Consórcios (quantidade de cotas incompatível, lances atípicos).
- Pessoas/entidades suspeitas de terrorismo (listas CSNU) — hit confirmado também exige indisponibilidade imediata de ativos (Lei 13.810/2019, Res. BCB 44/2020; ver 003 e 011).
- Atividades internacionais e jurisdições de alto risco (GAFI).
- Funcionários, parceiros e prestadores.
- Campanhas eleitorais.
- Municípios de risco elevado.

## Regras Operacionais Adicionais (boas práticas)
- Velocity: número de transações em janela curta.
- Peer group: desvio significativo do comportamento de clientes similares.
- Contraparte de alto risco ou em lista.
- Mudança de perfil seguida de movimentação atípica.
- Operações round-trip ou circular.

## Gestão de Alertas
- Deduplicação configurável.
- Severidade baseada em risco do cliente + tipo de cenário.
- SLA diferenciado por severidade e por condição de PEP.
