import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addCaseNote,
  approveCase,
  decideCase,
  escalateCase,
  getCase,
  getCaseDossier,
  listCaseEvidences,
  listCaseNotes,
  listCases,
  rejectCaseApproval,
  uploadCaseEvidence,
} from '@/api/cases'
import type { CaseDecision, CaseFilters, PageParams, UUID } from '@/api/types'

export const useCases = (filters: CaseFilters, paging: PageParams) =>
  useQuery({ queryKey: ['cases', 'list', filters, paging], queryFn: () => listCases(filters, paging), placeholderData: keepPreviousData })

export const useCase = (id: UUID | undefined) =>
  useQuery({ queryKey: ['cases', 'detail', id], queryFn: () => getCase(id!), enabled: !!id })

export const useCaseDossier = (id: UUID | undefined) =>
  useQuery({ queryKey: ['cases', id, 'dossier'], queryFn: () => getCaseDossier(id!), enabled: !!id })

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

/** Rejeição da aprovação (quatro olhos): o case volta a IN_ANALYSIS. */
export const useRejectApproval = () =>
  useCaseAction((v: { id: UUID; justification: string }) => rejectCaseApproval(v.id, { justification: v.justification }), 'Aprovação rejeitada. O case voltou para análise.', [['audit']])

export const useAddNote = (id: UUID) => useCaseAction((text: string) => addCaseNote(id, text), 'Nota adicionada.', [['cases', id, 'notes'], ['cases', id, 'dossier']])

export const useUploadEvidence = (id: UUID) =>
  useCaseAction((v: { file: File; description?: string }) => uploadCaseEvidence(id, v.file, v.description), 'Evidência anexada.', [['cases', id, 'evidences'], ['cases', id, 'dossier']])
