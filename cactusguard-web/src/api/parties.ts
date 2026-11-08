import { api } from './client'
import { cleanParams, pageQuery } from './params'
import type {
  AuditEntry,
  BeneficialOwner,
  Page,
  PageParams,
  Party,
  PartyAddress,
  PartyFilters,
  PartyRelationship,
  PartyRequest,
  PepRequest,
  PepResponse,
  RevealRequest,
  RevealResponse,
  RiskHistoryEntry,
  ScreeningResult,
  Transaction,
  UUID,
} from './types'

export async function getParty(id: UUID): Promise<Party> {
  return (await api.get<Party>(`/parties/${id}`)).data
}

/**
 * Lista partes com filtros combináveis `q`, `document` (igualdade por HMAC), `type`, `riskLevel`, `pep`, `sourceType`.
 * `q` e `document` são PII em potencial: vão só na requisição, nunca na URL do navegador.
 */
export async function listParties(filters: PartyFilters, paging: PageParams): Promise<Page<Party>> {
  const params = { ...pageQuery(paging), ...cleanParams({ ...filters, q: filters.q?.trim(), document: filters.document?.trim() }) }
  return (await api.get<Page<Party>>('/parties', { params })).data
}

export async function createParty(body: PartyRequest): Promise<Party> {
  return (await api.post<Party>('/parties', body)).data
}

/**
 * `PUT /parties/{id}`: o documento é imutável (omitido/mascarado/igual = ignorado; diferente = 422 DOCUMENT_IMMUTABLE)
 * e email/phone/motherName mascarados (`*`) ou null mantêm o valor atual.
 */
export async function updateParty(id: UUID, body: PartyRequest): Promise<Party> {
  return (await api.put<Party>(`/parties/${id}`, body)).data
}

export async function listAddresses(partyId: UUID): Promise<PartyAddress[]> {
  return (await api.get<PartyAddress[]>(`/parties/${partyId}/addresses`)).data
}

export async function listRelationships(partyId: UUID): Promise<PartyRelationship[]> {
  return (await api.get<PartyRelationship[]>(`/parties/${partyId}/relationships`)).data
}

/** `GET /parties/{id}/pep`: situação PEP atual. */
export async function getPep(partyId: UUID): Promise<PepResponse> {
  return (await api.get<PepResponse>(`/parties/${partyId}/pep`)).data
}

/** `POST /parties/{id}/pep`: marca/atualiza PEP (força EDD e risco mínimo HIGH). */
export async function markPep(partyId: UUID, body: PepRequest): Promise<PepResponse> {
  return (await api.post<PepResponse>(`/parties/${partyId}/pep`, body)).data
}

/** `POST /parties/{id}/pep/approve?comment=`: só SUPERVISOR. Comentário vai no corpo (form) para não ir à URL. */
export async function approvePep(partyId: UUID, comment: string): Promise<PepResponse> {
  return (await api.post<PepResponse>(`/parties/${partyId}/pep/approve`, new URLSearchParams({ comment }))).data
}

/** Recalcula o risco de UMA parte (ANALYST, SUPERVISOR). O global `POST /risk/recalculate` é ADMIN. */
export async function recalculateRisk(partyId: UUID): Promise<Party> {
  return (await api.post<Party>(`/parties/${partyId}/risk/recalculate`)).data
}

/** Revela PII (ANALYST/SUPERVISOR/AUDITOR; ADMIN recebe 403). Registra data_access_log + REVELACAO: motivo. Resposta `no-store`. */
export async function revealPartyField(id: UUID, body: RevealRequest): Promise<RevealResponse> {
  return (await api.post<RevealResponse>(`/parties/${id}/reveal`, body)).data
}

export async function listBeneficialOwners(partyId: UUID): Promise<BeneficialOwner[]> {
  return (await api.get<BeneficialOwner[]>(`/parties/${partyId}/beneficial-owners`)).data
}

export async function listRiskHistory(partyId: UUID, paging: PageParams = {}): Promise<Page<RiskHistoryEntry>> {
  return (await api.get<Page<RiskHistoryEntry>>(`/risk-history/party/${partyId}`, { params: pageQuery(paging) })).data
}

export async function listScreening(partyId: UUID): Promise<ScreeningResult[]> {
  return (await api.get<ScreeningResult[]>(`/screening/party/${partyId}`)).data
}

export async function listPartyTransactions(partyId: UUID, paging: PageParams = {}): Promise<Page<Transaction>> {
  return (await api.get<Page<Transaction>>(`/transactions/party/${partyId}`, { params: pageQuery(paging) })).data
}

export async function getTransaction(id: UUID): Promise<Transaction> {
  return (await api.get<Transaction>(`/transactions/${id}`)).data
}

/** Restrito a AUDITOR/ADMIN/SUPERVISOR no backend. */
export async function listAudit(entityType: string, entityId: UUID, paging: PageParams = {}): Promise<Page<AuditEntry>> {
  return (await api.get<Page<AuditEntry>>(`/audit/entity/${entityType}/${entityId}`, { params: pageQuery(paging) })).data
}
