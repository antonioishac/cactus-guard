# 011 — Screening, Listas e Mídia Adversa

## Tipos de Screening
1. **Cliente** (onboarding e contínuo).
2. **UBO e representantes**.
3. **Contraparte de transação** (batch ou near real-time).
4. **Funcionários (KYE)**.
5. **Parceiros e fornecedores (KYP/KYS)**.

## Listas Obrigatórias / Recomendadas
- PEPs (brasileiros e internacionais) + familiares e estreitos colaboradores.
- Sanções: OFAC, ONU/CSNU, UE, UK, listas nacionais.
- Listas internas do tenant.
- Listas de jurisdições de alto risco (GAFI).
- Mídia adversa (adverse media).

## Regras Operacionais
- Screening na entrada e rechecagem periódica (frequência proporcional ao risco).
- Fuzzy matching configurável (com gestão de homônimos).
- Hit potencial gera alerta ou tarefa de revisão.
- Confirmação de hit deve poder elevar risco e disparar EDD.
- Histórico completo de todas as checagens.
- Gestão de falsos positivos documentada (com justificativa).

## Contraparte
- Transações devem poder ser filtradas contra listas de sanções e PEPs da contraparte.
- Em caso de hit, a operação pode ser bloqueada, alertada ou escalada conforme política do tenant.

## CSNU — Indisponibilidade de Ativos (Lei 13.810/2019, Res. BCB 44/2020)
- Hit confirmado em sanção do CSNU **não** segue a política configurável do tenant: bloqueio (indisponibilidade) imediato e obrigatório, sem aviso prévio ao cliente.
- Atualização da lista CSNU dispara rescreening de toda a base ativa (clientes, UBOs, representantes, contrapartes).
- Desambiguação de homônimos usa nome da mãe, data de nascimento, nacionalidade e documento (ver 046).
- Requisitos de comunicação e prova de tempestividade: ver 003.
