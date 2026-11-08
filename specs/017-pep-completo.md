# 017 — Tratamento Completo de PEPs (Sem Lacunas)

## Definição (Circular 3.978, Art. 27)
Pessoas que desempenham ou desempenharam, nos últimos 5 anos, cargos/funções públicas relevantes no Brasil ou exterior.

Inclui (não exaustivo, conforme Art. 27):
- Mandatos eletivos União.
- Ministros, cargos de natureza especial, diretores de administração indireta, DAS 6.
- Membros CNJ, STF, tribunais superiores e regionais.
- Membros CNMP, PGR e equivalentes.
- TCU e MP junto ao TCU.
- Presidentes e tesoureiros de partidos políticos.
- Governadores, secretários de estado, deputados estaduais, presidentes de entidades estaduais/distritais.
- Prefeitos, vereadores, secretários municipais e equivalentes (conforme norma).
- No exterior: chefes de estado/governo, políticos de escalões superiores, oficiais-generais, etc.
- Dirigentes de organizações internacionais.

## Relacionados (Art. 19)
Obrigatório identificar:
- **Familiares**: parentes em linha reta ou colateral até 2º grau, cônjuge, companheiro(a), enteado(a).
- **Estreitos colaboradores**: pessoas com relação próxima conhecida (participação conjunta em PJ, mandatários, controle de entidades criadas para benefício do PEP).
- Representantes / procuradores.

## Regras Obrigatórias
- Status PEP ativo por 5 anos após deixar o cargo.
- Identificação na entrada e rechecagem periódica.
- EDD obrigatória: aprovação da alta administração para estabelecer ou manter relacionamento (Circular 3.978 — **[validar jurídico: número do artigo]**). Registrar aprovador, data e justificativa (ver 046).
- Expiração: `pep_expiry_date` = data de saída do cargo + 5 anos; após expirar, o status muda para `expired` com alerta, sem apagar o histórico.
- Diligência reforçada contínua.
- Monitoramento específico (cenários da Carta 4.001 relacionados a PEPs).
- Screening de familiares e colaboradores.
- Histórico versionado do status PEP e relacionamentos.
- Alertas automáticos em mudanças de status ou novos hits.

## Implementação
- Tabela PEPRelationship.
- Flag e campos em Party.
- Integração com listas de PEPs (internas + provedores).
- Workflow de EDD específico.
- Regras de monitoramento com limiares mais baixos para PEPs.