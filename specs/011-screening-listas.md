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
