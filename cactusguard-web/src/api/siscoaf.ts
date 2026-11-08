import { api } from './client'
import { cleanParams, pageQuery } from './params'
import type {
  Page,
  PageParams,
  SiscoafApproveRequest,
  SiscoafCommunication,
  SiscoafFilters,
  SiscoafReasonRequest,
  SiscoafSubmitRequest,
  UUID,
} from './types'

/** Sem `status` lista todos. `status`/`type` aceitam CSV. */
export async function listSiscoaf(filters: SiscoafFilters, paging: PageParams): Promise<Page<SiscoafCommunication>> {
  const params = cleanParams({ ...filters, ...pageQuery(paging) })
  return (await api.get<Page<SiscoafCommunication>>('/siscoaf', { params })).data
}

/** Inclui `statusHistory`. */
export async function getSiscoaf(id: UUID): Promise<SiscoafCommunication> {
  return (await api.get<SiscoafCommunication>(`/siscoaf/${id}`)).data
}

/** SUPERVISOR. DRAFT|PENDING_REVIEW -> APPROVED (não envia). Quatro olhos quando `fourEyesRequired`. */
export async function approveSiscoaf(id: UUID, body: SiscoafApproveRequest): Promise<SiscoafCommunication> {
  return (await api.post<SiscoafCommunication>(`/siscoaf/${id}/approve`, body)).data
}

/** `reason` opcional (>= 20 se informado); fica no histórico e no audit_log. */
export async function submitSiscoaf(id: UUID, body: SiscoafSubmitRequest): Promise<SiscoafCommunication> {
  return (await api.post<SiscoafCommunication>(`/siscoaf/${id}/submit`, body)).data
}

export async function cancelSiscoaf(id: UUID, body: SiscoafReasonRequest): Promise<SiscoafCommunication> {
  return (await api.post<SiscoafCommunication>(`/siscoaf/${id}/cancel`, body)).data
}

/** Retorna o NOVO rascunho (vinculado por `originalCommunicationId`); a original vira RECTIFIED. */
export async function rectifySiscoaf(id: UUID, body: SiscoafReasonRequest): Promise<SiscoafCommunication> {
  return (await api.post<SiscoafCommunication>(`/siscoaf/${id}/rectify`, body)).data
}

/** Falha 4xx (BUSINESS_RULE) com o motivo do bloqueio (ex.: há COS/COE enviadas no ano) em `message`. */
export async function generateNegativeDeclaration(year: number): Promise<SiscoafCommunication> {
  return (await api.post<SiscoafCommunication>(`/siscoaf/negative-declaration/${year}`)).data
}
