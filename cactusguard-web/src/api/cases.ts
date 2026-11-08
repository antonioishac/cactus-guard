import { api } from './client'
import { cleanParams, pageQuery } from './params'
import type {
  Case,
  CaseApprovalRequest,
  CaseCreateRequest,
  CaseDecisionRequest,
  CaseDossier,
  CaseEscalationRequest,
  CaseEvidence,
  CaseFilters,
  CaseNote,
  CaseRejectApprovalRequest,
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

/** Dossiê consolidado (PII mascarada; registra data_access_log). */
export async function getCaseDossier(id: UUID): Promise<CaseDossier> {
  return (await api.get<CaseDossier>(`/cases/${id}/dossier`)).data
}

/** Seleciona o alerta e inicia os 45 dias de análise. Justificativa >= 20 não brancos. */
export async function createCase(body: CaseCreateRequest): Promise<Case> {
  return (await api.post<Case>('/cases', body)).data
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

/** Quatro olhos: rejeita a decisão proposta; o case volta a IN_ANALYSIS e aceita nova decisão. */
export async function rejectCaseApproval(id: UUID, body: CaseRejectApprovalRequest): Promise<Case> {
  return (await api.post<Case>(`/cases/${id}/reject-approval`, body)).data
}

export async function listCaseNotes(id: UUID): Promise<CaseNote[]> {
  return (await api.get<CaseNote[]>(`/cases/${id}/notes`)).data
}

export async function addCaseNote(id: UUID, text: string): Promise<CaseNote> {
  return (await api.post<CaseNote>(`/cases/${id}/notes`, { text })).data
}

export async function listCaseEvidences(id: UUID): Promise<CaseEvidence[]> {
  return (await api.get<CaseEvidence[]>(`/cases/${id}/evidences`)).data
}

/** multipart: `file` + `description?`. PDF/JPG/PNG, máx. 10 MB (validado no servidor). */
export async function uploadCaseEvidence(id: UUID, file: File, description?: string): Promise<CaseEvidence> {
  const form = new FormData()
  form.append('file', file)
  if (description?.trim()) form.append('description', description.trim())
  return (await api.post<CaseEvidence>(`/cases/${id}/evidences`, form)).data
}

export async function downloadCaseEvidence(id: UUID, evidenceId: UUID): Promise<Blob> {
  return (await api.get<Blob>(`/cases/${id}/evidences/${evidenceId}`, { responseType: 'blob' })).data
}
