import { api } from './client'
import { cleanParams, pageQuery } from './params'
import type {
  Alert,
  AlertDetail,
  AlertEscalateRequest,
  AlertFilters,
  FalsePositiveRequest,
  Page,
  PageParams,
  UUID,
} from './types'

/** Backend hoje só filtra por status/severity; demais filtros (spec 033) são enviados e ignorados. */
export async function listAlerts(filters: AlertFilters, paging: PageParams): Promise<Page<Alert>> {
  const params = cleanParams({ ...filters, ...pageQuery(paging) })
  return (await api.get<Page<Alert>>('/alerts', { params })).data
}

export async function getAlert(id: UUID): Promise<AlertDetail> {
  return (await api.get<AlertDetail>(`/alerts/${id}`)).data
}

export async function assumeAlert(id: UUID): Promise<Alert> {
  return (await api.post<Alert>(`/alerts/${id}/assume`)).data
}

/** DIVERGÊNCIA: endpoint previsto na spec 033, ausente no AlertController atual. */
export async function escalateAlert(id: UUID, body: AlertEscalateRequest): Promise<Alert> {
  return (await api.post<Alert>(`/alerts/${id}/escalate`, body)).data
}

export async function markFalsePositive(id: UUID, body: FalsePositiveRequest): Promise<Alert> {
  return (await api.post<Alert>(`/alerts/${id}/false-positive`, body)).data
}
