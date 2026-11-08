import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  approveKyc,
  approveKycDocument,
  completeChecklistItem,
  listChecklist,
  listKycByParty,
  listKycDocuments,
  rejectKyc,
  startOnboarding,
  uploadKycDocument,
} from '@/api/kyc'
import type { UUID } from '@/api/types'

export const useKycProcesses = (partyId: UUID | undefined) =>
  useQuery({ queryKey: ['party', partyId, 'kyc'], queryFn: () => listKycByParty(partyId!), enabled: !!partyId, retry: false })

export const useChecklist = (processId: UUID) =>
  useQuery({ queryKey: ['kyc', processId, 'checklist'], queryFn: () => listChecklist(processId), retry: false })

export const useKycDocuments = (processId: UUID) =>
  useQuery({ queryKey: ['kyc', processId, 'documents'], queryFn: () => listKycDocuments(processId), retry: false })

function useKycAction<V, R>(fn: (v: V) => Promise<R>, successMessage: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    meta: { successMessage },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['kyc'] })
      void qc.invalidateQueries({ queryKey: ['party'] })
      void qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}

export const useStartOnboarding = () => useKycAction((partyId: UUID) => startOnboarding(partyId), 'Processo de KYC iniciado.')
export const useApproveKyc = () => useKycAction((id: UUID) => approveKyc(id), 'KYC aprovado.')
export const useRejectKyc = () => useKycAction((id: UUID) => rejectKyc(id), 'KYC reprovado.')
export const useCompleteItem = () => useKycAction((id: UUID) => completeChecklistItem(id), 'Item concluído.')
export const useApproveDocument = () => useKycAction((id: UUID) => approveKycDocument(id), 'Documento aprovado.')
export const useUploadDocument = () =>
  useKycAction((v: { processId: UUID; documentType: string; file: File }) => uploadKycDocument(v.processId, v.documentType, v.file), 'Documento enviado.')
