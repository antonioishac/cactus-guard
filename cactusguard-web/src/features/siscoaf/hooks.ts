import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { approveSiscoaf, cancelSiscoaf, generateNegativeDeclaration, getSiscoaf, listSiscoaf, rectifySiscoaf, submitSiscoaf } from '@/api/siscoaf'
import type { PageParams, SiscoafFilters, UUID } from '@/api/types'

export const useSiscoafList = (filters: SiscoafFilters, paging: PageParams) =>
  useQuery({ queryKey: ['siscoaf', 'list', filters, paging], queryFn: () => listSiscoaf(filters, paging), placeholderData: keepPreviousData })

export const useSiscoaf = (id: UUID | undefined) =>
  useQuery({ queryKey: ['siscoaf', 'detail', id], queryFn: () => getSiscoaf(id!), enabled: !!id })

function useSiscoafAction<V, R>(fn: (v: V) => Promise<R>, successMessage: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    meta: { successMessage },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['siscoaf'] })
      void qc.invalidateQueries({ queryKey: ['audit'] })
      void qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}

type WithJustification = { id: UUID; justification: string }

export const useApproveSiscoaf = () =>
  useSiscoafAction((v: WithJustification) => approveSiscoaf(v.id, { comment: v.justification }), 'Comunicação aprovada.')
export const useSubmitSiscoaf = () =>
  useSiscoafAction((v: WithJustification) => submitSiscoaf(v.id, { reason: v.justification }), 'Comunicação enviada ao COAF.')
export const useCancelSiscoaf = () =>
  useSiscoafAction((v: WithJustification) => cancelSiscoaf(v.id, { reason: v.justification }), 'Comunicação cancelada.')
export const useRectifySiscoaf = () =>
  useSiscoafAction((v: WithJustification) => rectifySiscoaf(v.id, { reason: v.justification }), 'Retificação criada como novo rascunho.')

/** O motivo do bloqueio é mostrado inline pela tela (vindo do backend), não em toast. */
export const useNegativeDeclaration = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (year: number) => generateNegativeDeclaration(year),
    meta: { silent: true, successMessage: 'Declaração negativa gerada como rascunho.' },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['siscoaf'] }),
  })
}
