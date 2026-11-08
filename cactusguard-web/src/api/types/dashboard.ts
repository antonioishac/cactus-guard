import type { AlertStatus, Severity } from './alerts'

/**
 * GET /dashboard/operational. O OpenAPI o declara como mapa genérico (sem DTO): o formato abaixo
 * não é verificado contra o schema gerado.
 */
export interface OperationalDashboard {
  openAlerts: number
  openCases: number
  pendingKyc: number
  criticalAlerts: number
  alertCaseRatio: number
}

/** GET /dashboard/metrics (também sem DTO no OpenAPI). */
export interface DashboardMetrics {
  alertsByStatus: Partial<Record<AlertStatus, number>>
  /** hits de screening pendentes */
  screenings: number
  activePeps: number
  openCases: number
  pendingApproval: number
}

/** GET /dashboard/alerts-chart?days=7 (1–90): zeros nos dias vazios. */
export interface AlertsChartPoint {
  date: string
  total: number
}

/** GET /dashboard/severity-distribution: sempre as 4 severidades. */
export interface SeverityDistributionItem {
  severity: Severity
  count: number
}

export interface DeadlineBuckets {
  onTime: number
  dueSoon: number
  overdue: number
}

/** GET /dashboard/case-sla: classificado pelo backend. */
export interface CaseSlaSummary {
  selection: DeadlineBuckets
  analysis: DeadlineBuckets
  communication: DeadlineBuckets
  pendingApproval: number
}
