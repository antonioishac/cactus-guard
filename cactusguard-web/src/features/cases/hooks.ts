import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addCaseNote,
  approveCase,
  decideCase,
  escalateCase,
  getCase,
  listCaseEvidences,
  listCaseNotes,
  listCases,
  uploadCaseEvidence,
} from '@/api/cases'
import type { CaseDecision, CaseFilters, PageParams, UUID } from '@/api/types'

export const useCases = (filters: CaseFilters, paging: PageParams) =>
  useQuery({ queryKey: ['cases', 'list', filters, paging], queryFn: () => listCases(filters, paging), placeholderData: keepPreviousData })

export const useCase = (id: UUID | undefined) =>
  useQuery({ queryKey: ['cases', 'detail', id], queryFn: () => getCase(id!), enabled: !!id })

// Notas e evidências ainda não têm endpoint no backend: sem retry, a seção mostra "indisponível".
export const useCaseNotes = (id: UUID | undefined) =>
  useQuery({ queryKey: ['cases', id, 'notes'], queryFn: () => listCaseNotes(id!), enabled: !!id, retry: false })

export const useCaseEvidences = (id: UUID | undefined) =>
  useQuery({ queryKey: ['cases', id, 'evidences'], queryFn: () => listCaseEvidences(id!), enabled: !!id, retry: false })

function useCaseAction<V, R>(fn: (v: V) => Promise<R>, successMessage: string, extraKeys: unknown[][] = []) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    meta: { successMessage },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['cases'] })
      void qc.invalidateQueries({ queryKey: ['dashboard'] })
      extraKeys.forEach((k) => void qc.invalidateQueries({ queryKey: k }))
    },
  })
}

export const useDecideCase = () =>
  useCaseAction(
    (v: { id: UUID; decision: CaseDecision; justification: string }) => decideCase(v.id, { decision: v.decision, justification: v.justification }),
    'Decisão registrada.',
    [['audit']],
  )

export const useEscalateCase = () =>
  useCaseAction((v: { id: UUID; justification: string }) => escalateCase(v.id, { justification: v.justification }), 'Case escalado.', [['audit']])

/** Backend recebe `{ comment }`; a justificativa digitada é enviada como comentário de aprovação. */
export const useApproveCase = () =>
  useCaseAction((v: { id: UUID; justification: string }) => approveCase(v.id, { comment: v.justification }), 'Decisão aprovada.', [['audit']])

export const useAddNote = (id: UUID) => useCaseAction((text: string) => addCaseNote(id, text), 'Nota adicionada.', [['cases', id, 'notes']])

export const useUploadEvidence = (id: UUID) =>
  useCaseAction((file: File) => uploadCaseEvidence(id, file), 'Evidência anexada.', [['cases', id, 'evidences']])
