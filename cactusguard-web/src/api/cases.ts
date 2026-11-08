import { api } from './client'
import { cleanParams, pageQuery } from './params'
import type {
  Case,
  CaseApprovalRequest,
  CaseDecisionRequest,
  CaseEscalationRequest,
  CaseEvidence,
  CaseFilters,
  CaseNote,
  Page,
  PageParams,
  UUID,
} from './types'

export async function listCases(filters: CaseFilters, paging: PageParams): Promise<Page<Case>> {
  const params = cleanParams({ ...filters, ...pageQuery(paging) })
  return (await api.get<Page<Case>>('/cases', { params })).data
}

export async function getCase(id: UUID): Promise<Case> {
  return (await api.get<Case>(`/cases/${id}`)).data
}

/** DIVERGÊNCIA: backend = POST /cases/from-alert/{alertId} sem corpo; spec 033 = POST /cases {alertId, justification}. */
export async function createCaseFromAlert(alertId: UUID): Promise<Case> {
  return (await api.post<Case>(`/cases/from-alert/${alertId}`)).data
}

export async function decideCase(id: UUID, body: CaseDecisionRequest): Promise<Case> {
  return (await api.post<Case>(`/cases/${id}/decide`, body)).data
}

export async function escalateCase(id: UUID, body: CaseEscalationRequest): Promise<Case> {
  return (await api.post<Case>(`/cases/${id}/escalate`, body)).data
}

export async function approveCase(id: UUID, body: CaseApprovalRequest): Promise<Case> {
  return (await api.post<Case>(`/cases/${id}/approve`, body)).data
}

/* ---- Contrato PROPOSTO (sem endpoint no backend): notas e evidências do case ---- */

export async function listCaseNotes(id: UUID): Promise<CaseNote[]> {
  return (await api.get<CaseNote[]>(`/cases/${id}/notes`)).data
}

export async function addCaseNote(id: UUID, text: string): Promise<CaseNote> {
  return (await api.post<CaseNote>(`/cases/${id}/notes`, { text })).data
}

export async function listCaseEvidences(id: UUID): Promise<CaseEvidence[]> {
  return (await api.get<CaseEvidence[]>(`/cases/${id}/evidences`)).data
}

export async function uploadCaseEvidence(id: UUID, file: File): Promise<CaseEvidence> {
  const form = new FormData()
  form.append('file', file)
  return (await api.post<CaseEvidence>(`/cases/${id}/evidences`, form)).data
}

export async function downloadCaseEvidence(id: UUID, evidenceId: UUID): Promise<Blob> {
  return (await api.get<Blob>(`/cases/${id}/evidences/${evidenceId}/download`, { responseType: 'blob' })).data
}
