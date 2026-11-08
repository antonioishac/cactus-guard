import { api } from './client'
import type {
  AlertsChartPoint,
  CaseSlaSummary,
  DashboardMetrics,
  OperationalDashboard,
  SeverityDistributionItem,
} from './types'

export async function getOperational(): Promise<OperationalDashboard> {
  return (await api.get<OperationalDashboard>('/dashboard/operational')).data
}

/* Ausentes no backend (spec 045 / proposta): consumidos com degradação graciosa. */
export async function getMetrics(): Promise<DashboardMetrics> {
  return (await api.get<DashboardMetrics>('/dashboard/metrics')).data
}
export async function getAlertsChart(): Promise<AlertsChartPoint[]> {
  return (await api.get<AlertsChartPoint[]>('/dashboard/alerts-chart')).data
}
export async function getSeverityDistribution(): Promise<SeverityDistributionItem[]> {
  return (await api.get<SeverityDistributionItem[]>('/dashboard/severity-distribution')).data
}
export async function getCaseSla(): Promise<CaseSlaSummary> {
  return (await api.get<CaseSlaSummary>('/dashboard/case-sla')).data
}
