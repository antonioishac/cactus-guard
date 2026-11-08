import type { Case, SessionUser } from '@/api/types'

/**
 * Regras de VISIBILIDADE de botões (conveniência). A decisão real (status, quatro olhos, role) é do backend.
 */
export const isFinalCase = (c: Case) => ['ARCHIVED', 'CLOSED', 'COMMUNICATE'].includes(c.status)
export const isPendingApproval = (c: Case) => c.status === 'PENDING_APPROVAL'

/** Pode decidir/escalar: case ativo e fora de aprovação. */
export const isActionable = (c: Case) => !isFinalCase(c) && !isPendingApproval(c)

/** Quem propôs a decisão não aprova (quatro olhos). Só sabemos se o backend enviar `decidedById`. */
export const isOwnDecision = (c: Case, user: SessionUser | null) => !!user && !!c.decidedById && c.decidedById === user.id
