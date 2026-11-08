import type { ISODateTime, UUID } from './common'

export type RuleStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'ACTIVE' | 'INACTIVE'

export type ParamValue = string | number | boolean | null | ParamValue[] | { [k: string]: ParamValue }
export type RuleParams = Record<string, ParamValue>

/** MonitoringRuleResponse. Métricas (`alertsGenerated`, `falsePositiveRate`) cobrem os últimos 30 dias. */
export interface MonitoringRule {
  id: UUID
  code: string
  name: string
  description: string | null
  category: string
  severity: string
  active: boolean
  status: RuleStatus | string
  version: number
  parameters: RuleParams | null
  effectiveFrom: ISODateTime | null
  effectiveTo: ISODateTime | null
  thresholdValue: number | null
  thresholdPeriodDays: number | null
  createdById: UUID | null
  createdByName: string | null
  approvedById: UUID | null
  approvedByName: string | null
  approvedAt: ISODateTime | null
  carta4001Reference: string | null
  appliesToRiskLevels: string[] | null
  autoCommunication: boolean
  /** versão imutável vigente agora (GET /rules/{code}) */
  activeVersion: number | null
  alertsGenerated: number | null
  /** falsos positivos / alertas encerrados; null se nenhum encerrado */
  falsePositiveRate: number | null
}

/** RuleVersionResponse. `snapshot` = estado da regra na versão (inclui `parameters`). */
export interface RuleVersion {
  version: number
  snapshot: Record<string, unknown> | null
  changeReason: string | null
  approvedById: UUID | null
  approvedAt: ISODateTime | null
  effectiveFrom: ISODateTime | null
  effectiveTo: ISODateTime | null
  createdById: UUID | null
  createdByName: string | null
  approvedByName: string | null
}

/** GET /rules/{code}/metrics?from=&to= (padrão: últimos 30 dias). */
export interface RuleMetrics {
  code: string
  from: ISODateTime
  to: ISODateTime
  alertsGenerated: number
  alertsClosed: number
  falsePositives: number
  falsePositiveRate: number | null
}

/** `status` aceita vários (CSV) e prevalece sobre `includeInactive`. */
export interface RuleFilters {
  status?: string
  category?: string
  severity?: string
  q?: string
  includeInactive?: boolean
}

/** MonitoringRuleRequest. `reason` é obrigatório na edição (PUT). */
export interface RuleRequest {
  code?: string
  name: string
  description?: string
  category: string
  severity: string
  thresholdValue?: number | null
  thresholdPeriodDays?: number | null
  parameters?: RuleParams
  appliesToRiskLevels?: string[]
  carta4001Reference?: string
  reason?: string
}

export interface EngineRunResult {
  status: string
  alertsGenerated: number
}
