import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  approvePep,
  createParty,
  getParty,
  listAddresses,
  listAudit,
  listBeneficialOwners,
  listParties,
  listPartyTransactions,
  listRelationships,
  listRiskHistory,
  listScreening,
  markPep,
  recalculateRisk,
  updateParty,
} from '@/api/parties'
import type { PageParams, PartyFilters, PartyRequest, PepRequest, UUID } from '@/api/types'

const enabled = (id: UUID | null | undefined) => ({ enabled: !!id })

export const useParty = (id: UUID | null | undefined) =>
  useQuery({ queryKey: ['party', id], queryFn: () => getParty(id!), ...enabled(id) })

export const useBeneficialOwners = (id: UUID | null | undefined) =>
  useQuery({ queryKey: ['party', id, 'ubo'], queryFn: () => listBeneficialOwners(id!), ...enabled(id) })

export const useRiskHistory = (id: UUID | null | undefined) =>
  useQuery({ queryKey: ['party', id, 'risk'], queryFn: () => listRiskHistory(id!, { size: 10, sort: 'createdAt,desc' }), ...enabled(id) })

export const useScreening = (id: UUID | null | undefined) =>
  useQuery({ queryKey: ['party', id, 'screening'], queryFn: () => listScreening(id!), ...enabled(id) })

export const usePartyTransactions = (id: UUID | null | undefined, size = 10) =>
  useQuery({
    queryKey: ['party', id, 'transactions', size],
    queryFn: () => listPartyTransactions(id!, { size, sort: 'transactionDate,desc' }),
    ...enabled(id),
  })

/** Trilha de auditoria (AUDITOR/ADMIN/SUPERVISOR; demais recebem 403 e a seção explica). */
export const useAuditTrail = (entityType: string, id: UUID | null | undefined, allowed: boolean) =>
  useQuery({
    queryKey: ['audit', entityType, id],
    queryFn: () => listAudit(entityType, id!, { size: 50, sort: 'createdAt,desc' }),
    enabled: !!id && allowed,
    retry: false,
  })

/* ---------------- etapa 2A: ficha 360º ---------------- */

export const useParties = (filters: PartyFilters, paging: PageParams) =>
  useQuery({ queryKey: ['parties', 'list', filters, paging], queryFn: () => listParties(filters, paging), placeholderData: keepPreviousData })

export const useAddresses = (id: UUID | null | undefined) =>
  useQuery({ queryKey: ['party', id, 'addresses'], queryFn: () => listAddresses(id!), enabled: !!id, retry: false })

export const useRelationships = (id: UUID | null | undefined) =>
  useQuery({ queryKey: ['party', id, 'relationships'], queryFn: () => listRelationships(id!), enabled: !!id, retry: false })

/** Histórico completo (até 100) para a linha do tempo e o gráfico. */
export const useRiskTimeline = (id: UUID | null | undefined) =>
  useQuery({ queryKey: ['party', id, 'risk', 'timeline'], queryFn: () => listRiskHistory(id!, { size: 100, sort: 'createdAt,desc' }), enabled: !!id })

export const usePartyTransactionsPage = (id: UUID | null | undefined, page: number) =>
  useQuery({
    queryKey: ['party', id, 'transactions', 'page', page],
    queryFn: () => listPartyTransactions(id!, { page, size: 10, sort: 'transactionDate,desc' }),
    enabled: !!id,
    placeholderData: keepPreviousData,
  })

function usePartyAction<V, R>(fn: (v: V) => Promise<R>, successMessage: string, partyId?: UUID) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    meta: { successMessage },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['parties'] })
      void qc.invalidateQueries({ queryKey: partyId ? ['party', partyId] : ['party'] })
    },
  })
}

export const useCreateParty = () => usePartyAction((b: PartyRequest) => createParty(b), 'Parte cadastrada.')

export const useUpdateParty = (id: UUID) => usePartyAction((b: PartyRequest) => updateParty(id, b), 'Cadastro atualizado.', id)

export const useMarkPep = (id: UUID) => usePartyAction((b: PepRequest) => markPep(id, b), 'Condição de PEP registrada.', id)

export const useApprovePep = (id: UUID) => usePartyAction((comment: string) => approvePep(id, comment), 'Relacionamento com PEP aprovado.', id)

export const useRecalculateRisk = (id: UUID) => usePartyAction(() => recalculateRisk(id), 'Risco recalculado.', id)
