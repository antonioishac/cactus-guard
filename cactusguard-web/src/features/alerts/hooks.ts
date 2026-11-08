import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { addAlertComment, assumeAlert, escalateAlert, getAlert, listAlertComments, listAlerts, markFalsePositive } from '@/api/alerts'
import { createCase } from '@/api/cases'
import type { AlertFilters, FalsePositiveRequest, PageParams, UUID } from '@/api/types'

export const useAlerts = (filters: AlertFilters, paging: PageParams) =>
  useQuery({ queryKey: ['alerts', 'list', filters, paging], queryFn: () => listAlerts(filters, paging), placeholderData: keepPreviousData })

export const useAlert = (id: UUID | null | undefined) =>
  useQuery({ queryKey: ['alerts', 'detail', id], queryFn: () => getAlert(id!), enabled: !!id })

/** Após qualquer ação, refaz lista e detalhe do alerta (e cases, pois abrir case muda ambos). */
function useAlertAction<V, R>(fn: (v: V) => Promise<R>, successMessage: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    meta: { successMessage },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['alerts'] })
      void qc.invalidateQueries({ queryKey: ['cases'] })
      void qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}

export const useAssumeAlert = () => useAlertAction((id: UUID) => assumeAlert(id), 'Alerta assumido.')
export const useEscalateAlert = () =>
  useAlertAction((v: { id: UUID; justification: string }) => escalateAlert(v.id, { justification: v.justification }), 'Alerta escalado.')
export const useFalsePositive = () =>
  useAlertAction((v: { id: UUID } & FalsePositiveRequest) => markFalsePositive(v.id, { reason: v.reason, justification: v.justification }), 'Alerta marcado como falso positivo.')
export const useOpenCase = () =>
  useAlertAction((v: { alertId: UUID; justification: string }) => createCase(v), 'Case aberto. Prazo de análise iniciado.')

export const useAlertComments = (id: UUID | undefined) =>
  useQuery({ queryKey: ['alerts', id, 'comments'], queryFn: () => listAlertComments(id!), enabled: !!id })

export const useAddAlertComment = (id: UUID) => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (text: string) => addAlertComment(id, text),
    meta: { successMessage: 'Comentário adicionado.' },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['alerts', id, 'comments'] })
      void qc.invalidateQueries({ queryKey: ['alerts', 'detail', id] })
    },
  })
}
