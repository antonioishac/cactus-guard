import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { assumeAlert, escalateAlert, getAlert, listAlerts, markFalsePositive } from '@/api/alerts'
import { createCaseFromAlert } from '@/api/cases'
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
export const useOpenCase = () => useAlertAction((alertId: UUID) => createCaseFromAlert(alertId), 'Case aberto. Prazo de análise iniciado.')
