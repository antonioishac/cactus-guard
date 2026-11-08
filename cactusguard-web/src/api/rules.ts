import { api } from './client'
import { cleanParams, pageQuery } from './params'
import type { EngineRunResult, MonitoringRule, Page, PageParams, RuleFilters, RuleRequest, RuleVersion } from './types'

/**
 * Pagina no cliente. DIVERGÊNCIA: `GET /rules` devolve `List` (sem Page) e só regras ativas
 * (`findByActiveTrue`); por isso filtro/ordenação/paginação ficam aqui até o backend paginar.
 */
export function toPage<T>(items: T[], paging: PageParams): Page<T> {
  const { page = 0, size = 20 } = pageQuery(paging)
  const [field, dir] = (paging.sort ?? '').split(',')
  const sorted = [...items]
  if (field) {
    sorted.sort((a, b) => {
      const av = (a as Record<string, unknown>)[field]
      const bv = (b as Record<string, unknown>)[field]
      if (av == null && bv == null) return 0
      if (av == null) return 1
      if (bv == null) return -1
      return (av < bv ? -1 : av > bv ? 1 : 0) * (dir === 'desc' ? -1 : 1)
    })
  }
  return { content: sorted.slice(page * size, page * size + size), totalElements: items.length, totalPages: Math.ceil(items.length / size), number: page, size }
}

export function filterRules(items: MonitoringRule[], f: RuleFilters): MonitoringRule[] {
  const q = f.q?.trim().toLowerCase()
  return items.filter(
    (r) =>
      (!f.status || r.status === f.status) &&
      (!f.category || r.category === f.category) &&
      (!f.severity || r.severity === f.severity) &&
      (!q || r.code.toLowerCase().includes(q) || r.name.toLowerCase().includes(q)),
  )
}

/** `includeInactive=true` é PROPOSTA (backend ignora e devolve só ativas); a tela avisa quando só vêm ativas. */
export async function listAllRules(): Promise<MonitoringRule[]> {
  const res = await api.get<MonitoringRule[] | Page<MonitoringRule>>('/rules', { params: { includeInactive: true } })
  return Array.isArray(res.data) ? res.data : res.data.content
}

export async function listRules(filters: RuleFilters, paging: PageParams): Promise<Page<MonitoringRule>> {
  return toPage(filterRules(await listAllRules(), filters), paging)
}

/** DIVERGÊNCIA: não há `GET /rules/{code}`; o detalhe localiza a regra na listagem. */
export async function getRule(code: string): Promise<MonitoringRule | null> {
  return (await listAllRules()).find((r) => r.code === code) ?? null
}

export async function listRuleVersions(code: string): Promise<RuleVersion[]> {
  return (await api.get<RuleVersion[]>(`/rules/${encodeURIComponent(code)}/versions`)).data
}

/** Backend cria já em PENDING_APPROVAL (inativa) — não existe "rascunho" separado. */
export async function createRule(body: RuleRequest): Promise<MonitoringRule> {
  return (await api.post<MonitoringRule>('/rules', body)).data
}

/** Gera nova versão e volta para PENDING_APPROVAL. `reason` obrigatório. */
export async function updateRule(code: string, body: RuleRequest): Promise<MonitoringRule> {
  return (await api.put<MonitoringRule>(`/rules/${encodeURIComponent(code)}`, body)).data
}

/** Backend recebe `comment` como query param. */
export async function approveRule(code: string, comment?: string): Promise<MonitoringRule> {
  const params = cleanParams({ comment })
  return (await api.post<MonitoringRule>(`/rules/${encodeURIComponent(code)}/approve`, null, { params })).data
}

/** active=false desativa; active=true SOLICITA ativação (vai para PENDING_APPROVAL). `reason` obrigatório. */
export async function toggleRule(code: string, active: boolean, reason: string): Promise<MonitoringRule> {
  return (await api.patch<MonitoringRule>(`/rules/${encodeURIComponent(code)}/toggle`, null, { params: { active, reason } })).data
}

export async function runRuleEngine(): Promise<EngineRunResult> {
  return (await api.post<EngineRunResult>('/rules/engine/evaluate')).data
}
