import { api } from './client'
import { pageQuery } from './params'
import type { KycChecklistItem, KycDocument, KycProcess, Page, UUID } from './types'

/** Todos os processos da parte (filtro `partyId` no servidor; `status` opcional). */
export async function listKycByParty(partyId: UUID): Promise<KycProcess[]> {
  return (await api.get<Page<KycProcess>>('/kyc', { params: { partyId, ...pageQuery({ size: 50, sort: 'createdAt,desc' }) } })).data.content
}

export async function startOnboarding(partyId: UUID): Promise<KycProcess> {
  return (await api.post<KycProcess>(`/kyc/onboarding/${partyId}`)).data
}

export async function approveKyc(id: UUID): Promise<KycProcess> {
  return (await api.post<KycProcess>(`/kyc/${id}/approve`)).data
}

export async function rejectKyc(id: UUID): Promise<KycProcess> {
  return (await api.post<KycProcess>(`/kyc/${id}/reject`)).data
}

export async function listChecklist(processId: UUID): Promise<KycChecklistItem[]> {
  return (await api.get<KycChecklistItem[]>(`/kyc/checklist/process/${processId}`)).data
}

export async function completeChecklistItem(id: UUID): Promise<KycChecklistItem> {
  return (await api.post<KycChecklistItem>(`/kyc/checklist/${id}/complete`)).data
}

export async function listKycDocuments(processId: UUID): Promise<KycDocument[]> {
  return (await api.get<KycDocument[]>(`/kyc/documents/process/${processId}`)).data
}

export async function uploadKycDocument(processId: UUID, documentType: string, file: File): Promise<KycDocument> {
  const form = new FormData()
  form.append('file', file)
  return (await api.post<KycDocument>(`/kyc/documents/${processId}`, form, { params: { documentType } })).data
}

export async function approveKycDocument(id: UUID): Promise<KycDocument> {
  return (await api.post<KycDocument>(`/kyc/documents/${id}/approve`)).data
}

export async function downloadKycDocument(id: UUID): Promise<Blob> {
  return (await api.get<Blob>(`/kyc/documents/${id}/download`, { responseType: 'blob' })).data
}
