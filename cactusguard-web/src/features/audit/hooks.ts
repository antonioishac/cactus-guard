import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { listAuditByEntity, listDataAccessByEntity, listDataAccessByUser } from '@/api/audit'
import type { PageParams } from '@/api/types'

export const useAuditByEntity = (type: string, id: string, paging: PageParams) =>
  useQuery({ queryKey: ['audit', 'entity', type, id, paging], queryFn: () => listAuditByEntity(type, id, paging), enabled: !!type && !!id, placeholderData: keepPreviousData })

export const useDataAccessByEntity = (type: string, id: string, paging: PageParams) =>
  useQuery({ queryKey: ['audit', 'access-entity', type, id, paging], queryFn: () => listDataAccessByEntity(type, id, paging), enabled: !!type && !!id, placeholderData: keepPreviousData })

export const useDataAccessByUser = (userId: string, paging: PageParams) =>
  useQuery({ queryKey: ['audit', 'access-user', userId, paging], queryFn: () => listDataAccessByUser(userId, paging), enabled: !!userId, placeholderData: keepPreviousData })
