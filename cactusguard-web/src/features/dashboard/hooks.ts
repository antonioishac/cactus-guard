import { useQuery } from '@tanstack/react-query'
import { listAlerts } from '@/api/alerts'
import { getAlertsChart, getCaseSla, getMetrics, getOperational, getSeverityDistribution } from '@/api/dashboard'

const REFRESH_MS = 60_000
const base = { refetchInterval: REFRESH_MS, retry: false } as const

export const useOperational = () => useQuery({ queryKey: ['dashboard', 'operational'], queryFn: getOperational, ...base, retry: 1 })
// Endpoints abaixo ainda não existem no backend real: sem retry, cada seção degrada sozinha.
export const useMetrics = () => useQuery({ queryKey: ['dashboard', 'metrics'], queryFn: getMetrics, ...base })
export const useAlertsChart = () => useQuery({ queryKey: ['dashboard', 'alerts-chart'], queryFn: getAlertsChart, ...base })
export const useSeverityDistribution = () => useQuery({ queryKey: ['dashboard', 'severity'], queryFn: getSeverityDistribution, ...base })
export const useCaseSla = () => useQuery({ queryKey: ['dashboard', 'case-sla'], queryFn: getCaseSla, ...base })

export const useRecentAlerts = () =>
  useQuery({
    queryKey: ['dashboard', 'recent-alerts'],
    queryFn: () => listAlerts({}, { page: 0, size: 5, sort: 'createdAt,desc' }),
    refetchInterval: REFRESH_MS,
  })

export const useCriticalAlerts = () =>
  useQuery({
    queryKey: ['dashboard', 'critical-alerts'],
    queryFn: () => listAlerts({ severity: 'CRITICAL', status: 'OPEN' }, { page: 0, size: 5, sort: 'selectionDeadline,asc' }),
    refetchInterval: REFRESH_MS,
  })
