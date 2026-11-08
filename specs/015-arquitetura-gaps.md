# 015 — Decisões de Arquitetura e Gaps Residuais

## Decisões de Arquitetura (para o início)

| Decisão | Escolha Recomendada | Justificativa |
|---------|---------------------|---------------|
| Banco principal | PostgreSQL + JSONB | Flexibilidade + RLS nativo para multi-tenancy |
| Multi-tenancy | Shared DB + `tenant_id` + filtro Hibernate (047) + RLS como defesa em profundidade | Simples para SaaS, escalável, auditável |
| Motor de regras (MVP) | JSON conditions + evaluator em código | Rápido de implementar e versionar; migrar para Drools depois se necessário |
| Ingestão | Batch primeiro (CSV/JSON) | Menor complexidade; eventos depois |
| Autenticação | JWT + RBAC simples | Suficiente para MVP |
| Auditoria | Tabela AuditLog append-only | Requisito regulatório crítico |

## Observação sobre o Escopo do MVP

O MVP foi redefinido como **completo** (ver `014-mvp-escopo-completo.md`). Os itens abaixo não são mais considerados gaps do MVP, mas evoluções futuras de diferenciação.

## Evoluções Pós-MVP (Diferenciação)

### Prioridade Alta
- Análise de grafos / redes avançada (detecção de relacionamentos complexos).
- IA para priorização de alertas e detecção de anomalias.
- Near real-time pleno com processamento por eventos em escala.

### Prioridade Média
- Dashboards altamente customizáveis e relatórios de efetividade avançados.
- Integração profunda com bases compartilhadas de fraude (NoTrick e similares).
- Private cloud e opções de isolamento enterprise avançadas.

### Prioridade Baixa
- Módulos específicos por segmento (câmbio, ativos virtuais, consórcios etc.).
- Otimizações de escala e performance para volumes muito altos.

## Recomendações de Especialista em Fraude
- Comece com regras que cruzem **comportamento + perfil** (não apenas limiares absolutos).
- Documente a lógica de cada regra desde o primeiro dia.
- Planeje a reconstrução histórica desde o modelo de dados.
- Trate a comunicação automática de espécie como fluxo separado e obrigatório.
- Não permita decisão de comunicar sem justificativa textual.
- Mantenha o sistema “conservative by default” em caso de dúvida regulatória.
