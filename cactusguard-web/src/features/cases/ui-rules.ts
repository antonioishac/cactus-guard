import type { Case, SessionUser } from '@/api/types'

/**
 * Regras de VISIBILIDADE de botões (conveniência). A decisão real (status, quatro olhos, role) é do backend.
 */
export const isFinalCase = (c: Case) => ['ARCHIVED', 'COMMUNICATED', 'COMMUNICATE'].includes(c.status)
/** Case encerrado não recebe notas nem evidências. */
export const isClosedCase = (c: Case) => ['ARCHIVED', 'COMMUNICATED'].includes(c.status)
export const isPendingApproval = (c: Case) => c.status === 'PENDING_APPROVAL'

/** Pode decidir/escalar: case ativo e fora de aprovação. */
export const isActionable = (c: Case) => !isFinalCase(c) && !isPendingApproval(c)

/** Quem propôs a decisão não aprova nem rejeita (quatro olhos): `decidedById` vem no CaseResponse. */
export const isOwnDecision = (c: Case, user: SessionUser | null) => !!user && !!c.decidedById && c.decidedById === user.id

/** Prazo vigente de um case: com decisão de comunicar aprovada vale a comunicação; senão a análise. */
export function currentDeadline(c: Pick<Case, 'status' | 'analysisDeadline' | 'communicationDeadline'>) {
  return c.status === 'COMMUNICATE'
    ? { deadline: c.communicationDeadline, phase: 'Comunicação (D+1 útil)' }
    : { deadline: c.analysisDeadline, phase: 'Análise (45 d)' }
}
