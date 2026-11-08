import { api } from './client'
import { cleanParams, pageQuery } from './params'
import type { Page, PageParams, SiscoafCommunication, SiscoafFilters, SiscoafReasonRequest, UUID } from './types'

/**
 * DIVERGÊNCIA: backend lista UM status por vez (`status`, default DRAFT) e não filtra por tipo/período.
 * `type` é enviado como proposta; a tela também filtra no cliente o que vier na página.
 */
export async function listSiscoaf(filters: SiscoafFilters, paging: PageParams): Promise<Page<SiscoafCommunication>> {
  const params = cleanParams({ ...filters, ...pageQuery(paging) })
  return (await api.get<Page<SiscoafCommunication>>('/siscoaf', { params })).data
}

export async function getSiscoaf(id: UUID): Promise<SiscoafCommunication> {
  return (await api.get<SiscoafCommunication>(`/siscoaf/${id}`)).data
}

/**
 * DIVERGÊNCIA: o backend NÃO tem endpoint de aprovação (submit aceita DRAFT/APPROVED e grava o aprovador).
 * `POST /siscoaf/{id}/approve` é PROPOSTA do front (fluxo revisão -> aprovação -> envio).
 */
export async function approveSiscoaf(id: UUID, body: { comment: string }): Promise<SiscoafCommunication> {
  return (await api.post<SiscoafCommunication>(`/siscoaf/${id}/approve`, body)).data
}

/** Backend não lê corpo; a justificativa vai em `reason` para a trilha quando o backend passar a aceitar. */
export async function submitSiscoaf(id: UUID, body: { reason: string }): Promise<SiscoafCommunication> {
  return (await api.post<SiscoafCommunication>(`/siscoaf/${id}/submit`, body)).data
}

export async function cancelSiscoaf(id: UUID, body: SiscoafReasonRequest): Promise<SiscoafCommunication> {
  return (await api.post<SiscoafCommunication>(`/siscoaf/${id}/cancel`, body)).data
}

/** Retorna o NOVO rascunho (vinculado por `originalCommunicationId`); a original vira RECTIFIED. */
export async function rectifySiscoaf(id: UUID, body: SiscoafReasonRequest): Promise<SiscoafCommunication> {
  return (await api.post<SiscoafCommunication>(`/siscoaf/${id}/rectify`, body)).data
}

/** Falha 4xx com o motivo do bloqueio (ex.: há COS/COE enviadas no ano) em `message`. */
export async function generateNegativeDeclaration(year: number): Promise<SiscoafCommunication> {
  return (await api.post<SiscoafCommunication>(`/siscoaf/negative-declaration/${year}`)).data
}
