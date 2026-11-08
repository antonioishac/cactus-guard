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
export async function getMetrics(): Promise<DashboardMetrics> {
  return (await api.get<DashboardMetrics>('/dashboard/metrics')).data
}
export async function getAlertsChart(days = 7): Promise<AlertsChartPoint[]> {
  return (await api.get<AlertsChartPoint[]>('/dashboard/alerts-chart', { params: { days } })).data
}
export async function getSeverityDistribution(): Promise<SeverityDistributionItem[]> {
  return (await api.get<SeverityDistributionItem[]>('/dashboard/severity-distribution')).data
}
export async function getCaseSla(): Promise<CaseSlaSummary> {
  return (await api.get<CaseSlaSummary>('/dashboard/case-sla')).data
}
