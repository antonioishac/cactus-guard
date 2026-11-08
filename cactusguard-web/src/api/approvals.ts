import { api } from './client'
import { pageQuery } from './params'
import type { PageParams, PendingApprovals } from './types'

/** SUPERVISOR. `page`/`size` valem para cada uma das três listas (mais antigos primeiro). */
export async function getPendingApprovals(paging: PageParams = {}): Promise<PendingApprovals> {
  return (await api.get<PendingApprovals>('/approvals/pending', { params: pageQuery(paging) })).data
}
