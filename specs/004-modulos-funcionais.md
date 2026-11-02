# 004 — Módulos Funcionais

## 1. Onboarding / KYC / KYB
- Identificação de PF e PJ.
- Qualificação (atividade, renda/faturamento, origem dos recursos, endereço etc.).
- Identificação de Beneficiário Final (UBO ≥ 25%).
- Classificação de risco inicial.
- Integração com provedores externos (opcional).

## 2. Classificação de Risco Contínua
- Scoring parametrizável.
- Revisão periódica e por eventos.
- Histórico versionado de classificações.

## 3. Screening Contínuo
- PEPs (brasileiros e internacionais).
- Listas de sanções (OFAC, ONU, UE, etc.).
- Listas internas do cliente.
- Gestão de falsos positivos e rechecagens periódicas.

## 4. Monitoramento Transacional
- Motor de regras configurável.
- Cobertura dos principais cenários da Carta Circular 4.001/2020.
- Processamento batch e near real-time (futuro).
- Agregados pré-calculados para performance.
- Detecção de:
  - Fracionamento / estruturação
  - Movimentação incompatível com perfil/capacidade
  - Contas-passagem
  - Operações com PEPs
  - Jurisdições de alto risco
  - Valores arredondados ou pouco abaixo de limiares
  - etc.

## 5. Case Management / Análise de Alertas
- Workflow configurável.
- SLAs regulatórios.
- Geração de dossiê.
- Anexação de documentos e evidências.
- Decisão documentada: comunicar ou arquivar.

## 6. Comunicação Regulatória
- Geração de comunicações para SISCOAF (COS e COE).
- Suporte a declaração negativa.
- Trilha completa de envio.

## 7. Governança e Auditoria
- Logs imutáveis.
- Relatórios de efetividade.
- Dashboards operacionais e gerenciais.
- Controle de acesso por função.
