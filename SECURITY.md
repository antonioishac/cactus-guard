# Política de Segurança — CactusGuard

O CactusGuard processa dados de PLD/FT de instituições reguladas pelo BCB. Levamos a sério
qualquer relato de vulnerabilidade e pedimos divulgação **coordenada**.

## Como reportar

- **Não abra issue pública**, PR ou discussão com detalhes da vulnerabilidade.
- Use o **GitHub Private Vulnerability Reporting** deste repositório
  (aba *Security* → *Report a vulnerability*).
- Inclua: componente afetado (backend, web, mobile, imagem, CI), versão/commit, passos de reprodução,
  impacto estimado e, se possível, prova de conceito mínima.
- **Não inclua dados pessoais reais** (CPF/CNPJ, nomes, dados de transação) nem dados de clientes no relato.

## O que esperar

| Etapa | Prazo-alvo |
|---|---|
| Confirmação de recebimento | 2 dias úteis |
| Triagem inicial e severidade (CVSS v4) | 5 dias úteis |
| Correção de severidade crítica/alta | 30 dias corridos (crítica: o quanto antes) |
| Correção de severidade média/baixa | próximo ciclo de release |

Mantemos o relator informado e combinamos a data de divulgação. Com sua autorização, damos crédito no advisory.

## Escopo

- Código deste repositório (`cactusguard-backend`, `cactusguard-web`, `cactusguard-mobile`), imagens publicadas
  em `ghcr.io` a partir dele e os workflows de CI/CD.
- **Fora de escopo**: credenciais de desenvolvimento versionadas de propósito (perfil `dev`/`test`, listadas em
  `.gitleaks.toml`) — são públicas e nunca válidas em produção; ataques que exijam acesso físico ou engenharia
  social; DoS volumétrico; achados de scanner automático sem impacto demonstrado.

## Porto seguro

Pesquisa de boa-fé, dentro do escopo, que evite acesso a dados de terceiros, degradação de serviço e
persistência, e que respeite esta política, não será alvo de ação por nossa parte. Testes contra ambientes de
clientes (instituições financeiras) **não** estão autorizados por esta política.

## Versões suportadas

Somente a versão mais recente da branch `main` (e a última tag `v*`) recebe correções de segurança.

## Segredo exposto

Se encontrar um segredo real (token, chave, senha de ambiente não-dev), reporte pelo canal acima. Do nosso
lado, o procedimento é: **revogar/rotacionar primeiro**, depois remover do código e avaliar o histórico.
