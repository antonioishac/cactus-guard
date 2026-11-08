import type { AlertStatus, Severity } from './alerts'

/** GET /dashboard/operational — único endpoint de dashboard que existe hoje (DashboardController). */
export interface OperationalDashboard {
  openAlerts: number
  openCases: number
  pendingKyc: number
  criticalAlerts: number
  alertCaseRatio: number
}

/* ---- Endpoints da spec 045 / propostos (ausentes no backend): seções degradam se 404 ---- */

/** GET /dashboard/metrics (spec 045) */
export interface DashboardMetrics {
  alertsByStatus: Partial<Record<AlertStatus, number>>
  screenings?: number
  activePeps?: number
}

/** GET /dashboard/alerts-chart (spec 045) — 7 dias */
export interface AlertsChartPoint {
  date: string
  total: number
}

/** GET /dashboard/severity-distribution (spec 045) */
export interface SeverityDistributionItem {
  severity: Severity
  count: number
}

export interface DeadlineBuckets {
  onTime: number
  dueSoon: number
  overdue: number
}

/** GET /dashboard/case-sla (PROPOSTO): cases por prazo regulatório, classificados pelo backend. */
export interface CaseSlaSummary {
  selection: DeadlineBuckets
  analysis: DeadlineBuckets
  communication: DeadlineBuckets
  pendingApproval: number
}
