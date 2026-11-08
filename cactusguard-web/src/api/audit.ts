import { api } from './client'
import { cleanParams, pageQuery } from './params'
import type { AuditEntry, AuditFilters, DataAccessEntry, Page, PageParams, UUID } from './types'

/** Trilha global paginada no servidor (padrão `createdAt,desc`). `from`/`to`: AAAA-MM-DD (America/Sao_Paulo). */
export async function listAudit(filters: AuditFilters, paging: PageParams): Promise<Page<AuditEntry>> {
  return (await api.get<Page<AuditEntry>>('/audit', { params: cleanParams({ ...filters, ...pageQuery(paging) }) })).data
}

/** Log LGPD de acesso a dados (mesmos filtros, sem `action`). */
export async function listDataAccess(filters: Omit<AuditFilters, 'action'>, paging: PageParams): Promise<Page<DataAccessEntry>> {
  return (await api.get<Page<DataAccessEntry>>('/data-access', { params: cleanParams({ ...filters, ...pageQuery(paging) }) })).data
}

export async function listAuditByEntity(entityType: string, entityId: UUID, paging: PageParams): Promise<Page<AuditEntry>> {
  return (await api.get<Page<AuditEntry>>(`/audit/entity/${encodeURIComponent(entityType)}/${entityId}`, { params: pageQuery(paging) })).data
}

export async function listDataAccessByEntity(entityType: string, entityId: UUID, paging: PageParams): Promise<Page<DataAccessEntry>> {
  return (await api.get<Page<DataAccessEntry>>(`/data-access/entity/${encodeURIComponent(entityType)}/${entityId}`, { params: pageQuery(paging) })).data
}

export async function listDataAccessByUser(userId: UUID, paging: PageParams): Promise<Page<DataAccessEntry>> {
  return (await api.get<Page<DataAccessEntry>>(`/data-access/user/${userId}`, { params: pageQuery(paging) })).data
}
