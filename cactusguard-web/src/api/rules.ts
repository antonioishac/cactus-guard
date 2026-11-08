import { api } from './client'
import { cleanParams, pageQuery } from './params'
import type { EngineRunResult, MonitoringRule, Page, PageParams, RuleFilters, RuleMetrics, RuleRequest, RuleVersion } from './types'

/** Paginado no servidor. Padrão = só ativas; `includeInactive` inclui todas; `status` (CSV) prevalece. */
export async function listRules(filters: RuleFilters, paging: PageParams): Promise<Page<MonitoringRule>> {
  const params = cleanParams({ ...filters, ...pageQuery(paging) })
  return (await api.get<Page<MonitoringRule>>('/rules', { params })).data
}

export async function getRule(code: string): Promise<MonitoringRule> {
  return (await api.get<MonitoringRule>(`/rules/${encodeURIComponent(code)}`)).data
}

/** Métricas dos últimos 30 dias por padrão. */
export async function getRuleMetrics(code: string, range: { from?: string; to?: string } = {}): Promise<RuleMetrics> {
  return (await api.get<RuleMetrics>(`/rules/${encodeURIComponent(code)}/metrics`, { params: cleanParams(range) })).data
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

/** Backend recebe `comment` como query param. Aprovador != criador (quatro olhos). */
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
