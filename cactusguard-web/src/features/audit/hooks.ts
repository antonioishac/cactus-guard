import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { listAudit, listDataAccess } from '@/api/audit'
import type { AuditFilters, PageParams } from '@/api/types'

/** Trilha global, filtrada e paginada no servidor. */
export const useAudit = (filters: AuditFilters, paging: PageParams, enabled = true) =>
  useQuery({ queryKey: ['audit', 'list', filters, paging], queryFn: () => listAudit(filters, paging), enabled, placeholderData: keepPreviousData })

export const useDataAccess = (filters: Omit<AuditFilters, 'action'>, paging: PageParams, enabled = true) =>
  useQuery({ queryKey: ['audit', 'access', filters, paging], queryFn: () => listDataAccess(filters, paging), enabled, placeholderData: keepPreviousData })
