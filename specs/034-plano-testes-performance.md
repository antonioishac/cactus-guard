# Spec 034 — Plano de Testes de Performance (Buscas e Listagens)

## Objetivo
Garantir que as buscas textuais (full-text) e as listagens principais do CactusGuard tenham desempenho aceitável sob volume realista de dados.

## Escopo
- Busca full-text em `Party` e `Alert`
- Listagens paginadas de Alertas, Cases e Partes
- Filtros combinados (status + data + full-text)

## Volume de referência para testes
| Entidade     | Volume mínimo para teste |
|--------------|---------------------------|
| Party        | 500.000                   |
| Alert        | 2.000.000                 |
| Case         | 300.000                   |
| Transaction  | 10.000.000 (particionada) |

## Cenários de teste

### 1. Busca full-text simples
- `GET /api/v1/parties?q=joao silva`
- `GET /api/v1/alerts?q=pix suspeito`
- Medir: tempo de resposta (p95 < 300 ms), uso de índice GIN.

### 2. Busca full-text + filtros
- `GET /api/v1/alerts?q=alto risco&status=OPEN&severity=HIGH`
- Medir se o planner usa índice de `tenant_id` + GIN.

### 3. Listagem paginada sem busca textual
- `GET /api/v1/alerts?status=OPEN&page=0&size=20`
- `GET /api/v1/cases?assignedTo=me&page=0&size=20`
- Meta: p95 < 200 ms.

### 4. Paginação profunda
- Página 500+ em listagens grandes.
- Verificar se há degradação (keyset pagination pode ser necessária no futuro).

## Como executar

1. **Carga de dados**
   - Script de carga (ou ferramenta) que popule o volume acima com dados realistas.
   - Incluir variação de nomes, descrições e status.

2. **EXPLAIN ANALYZE**
   - Rodar nas queries principais antes e depois da carga.
   - Confirmar uso de:
     - Índice GIN no `search_vector`
     - Índice em `tenant_id`
     - Índices compostos relevantes

3. **Teste de carga**
   - Ferramenta: k6, JMeter ou Gatling.
   - 50–100 usuários simultâneos fazendo buscas e listagens.
   - Duração: 10–15 minutos.
   - Coletar: p95, p99, taxa de erro, throughput.

4. **Critérios de aceite**
   - p95 das buscas full-text < 400 ms
   - p95 das listagens simples < 250 ms
   - Uso consistente dos índices (sem Seq Scan em tabelas grandes)
   - Sem degradação significativa com filtros combinados

## Ações se não passar
- Revisar composição do `tsvector`
- Adicionar índices compostos
- Avaliar keyset pagination
- Ajustar configuração do PostgreSQL (`work_mem`, `shared_buffers`)
- Considerar desnormalização adicional

## Responsabilidades
- Backend: queries, índices, EXPLAIN
- QA / Fullstack: roteiros de carga e validação dos critérios
- DevOps: ambiente com volume representativo
