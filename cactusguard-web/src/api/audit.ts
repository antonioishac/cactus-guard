import { api } from './client'
import { pageQuery } from './params'
import type { AuditEntry, DataAccessEntry, Page, PageParams, UUID } from './types'

/**
 * DIVERGÊNCIA: a trilha só pode ser consultada POR ENTIDADE (`/audit/entity/{tipo}/{id}`); não há busca
 * global por usuário/ação/período. A tela exige a entidade e aplica usuário/ação/período sobre a página recebida.
 */
export async function listAuditByEntity(entityType: string, entityId: UUID, paging: PageParams): Promise<Page<AuditEntry>> {
  return (await api.get<Page<AuditEntry>>(`/audit/entity/${encodeURIComponent(entityType)}/${entityId}`, { params: pageQuery(paging) })).data
}

export async function listDataAccessByEntity(entityType: string, entityId: UUID, paging: PageParams): Promise<Page<DataAccessEntry>> {
  return (await api.get<Page<DataAccessEntry>>(`/data-access/entity/${encodeURIComponent(entityType)}/${entityId}`, { params: pageQuery(paging) })).data
}

export async function listDataAccessByUser(userId: UUID, paging: PageParams): Promise<Page<DataAccessEntry>> {
  return (await api.get<Page<DataAccessEntry>>(`/data-access/user/${userId}`, { params: pageQuery(paging) })).data
}
