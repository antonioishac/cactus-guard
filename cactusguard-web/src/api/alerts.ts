import { api } from './client'
import { cleanParams, pageQuery } from './params'
import type {
  Alert,
  AlertComment,
  AlertDetail,
  AlertEscalateRequest,
  AlertFilters,
  FalsePositiveRequest,
  Page,
  PageParams,
  UUID,
} from './types'

/** Filtros múltiplos (status, severity) vão como CSV. `from`/`to`: AAAA-MM-DD sobre `createdAt`. */
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

/** Escalar não é decisão nem encerramento: alerta fica ESCALATED. */
export async function escalateAlert(id: UUID, body: AlertEscalateRequest): Promise<Alert> {
  return (await api.post<Alert>(`/alerts/${id}/escalate`, body)).data
}

export async function markFalsePositive(id: UUID, body: FalsePositiveRequest): Promise<Alert> {
  return (await api.post<Alert>(`/alerts/${id}/false-positive`, body)).data
}

export async function listAlertComments(id: UUID): Promise<AlertComment[]> {
  return (await api.get<AlertComment[]>(`/alerts/${id}/comments`)).data
}

export async function addAlertComment(id: UUID, text: string): Promise<AlertComment> {
  return (await api.post<AlertComment>(`/alerts/${id}/comments`, { text })).data
}
