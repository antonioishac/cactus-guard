import type { ISODateTime, UUID } from './common'

export type RuleStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'ACTIVE' | 'INACTIVE'

export type ParamValue = string | number | boolean | null | ParamValue[] | { [k: string]: ParamValue }
export type RuleParams = Record<string, ParamValue>

/** MonitoringRuleResponse do backend. */
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
  /* ---- NÃO existem no DTO real (entity/spec); opcionais ---- */
  carta4001Reference?: string | null
  appliesToRiskLevels?: string[] | null
  /** necessário para desabilitar "Aprovar" ao criador (quatro olhos) */
  createdById?: UUID | null
  createdByName?: string | null
  /** métricas (spec 013) */
  alertsGenerated?: number | null
  falsePositiveRate?: number | null
}

/** RuleVersionResponse do backend. `snapshot` = estado da regra na versão (inclui `parameters`). */
export interface RuleVersion {
  version: number
  snapshot: Record<string, unknown> | null
  changeReason: string | null
  approvedById: UUID | null
  approvedAt: ISODateTime | null
  effectiveFrom: ISODateTime | null
  effectiveTo: ISODateTime | null
  /** não existe no DTO real */
  createdById?: UUID | null
  createdByName?: string | null
  approvedByName?: string | null
}

export interface RuleFilters {
  status?: string
  category?: string
  severity?: string
  q?: string
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
