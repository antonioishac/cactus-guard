import type { Role } from '@/api/types'

/**
 * Conveniência de UI: espelha os @PreAuthorize do backend. O backend continua sendo a barreira real.
 * AUDITOR e ADMIN são somente leitura nas ações de alerta/case.
 */
export type Action =
  | 'alert:assume'
  | 'alert:escalate'
  | 'alert:falsePositive'
  | 'case:open'
  | 'case:decide'
  | 'case:escalate'
  | 'case:approve'
  | 'case:note'
  | 'case:upload'
  | 'pii:reveal'
  | 'audit:read'
  | 'party:write'
  | 'pep:mark'
  | 'pep:approve'
  | 'risk:recalculate'
  | 'screening:run'
  | 'screening:review'
  | 'watchlist:write'
  | 'kyc:write'
  | 'kyc:approve'
  | 'users:manage'
  | 'siscoaf:act'
  | 'rule:write'
  | 'rule:approve'
  | 'rule:run'

const ANALYSTS: Role[] = ['ANALYST', 'SUPERVISOR']

const MATRIX: Record<Action, Role[]> = {
  'alert:assume': ANALYSTS,
  'alert:escalate': ANALYSTS,
  'alert:falsePositive': ANALYSTS,
  'case:open': ANALYSTS,
  'case:decide': ANALYSTS,
  'case:escalate': ANALYSTS,
  'case:approve': ['SUPERVISOR'],
  'case:note': ANALYSTS,
  'case:upload': ANALYSTS,
  'pii:reveal': ANALYSTS,
  'audit:read': ['AUDITOR', 'ADMIN', 'SUPERVISOR'],
  'party:write': ANALYSTS,
  'pep:mark': ANALYSTS,
  'pep:approve': ['SUPERVISOR'],
  'risk:recalculate': ['SUPERVISOR', 'ADMIN'],
  'screening:run': ANALYSTS,
  'screening:review': ANALYSTS,
  'watchlist:write': ['SUPERVISOR'],
  'kyc:write': ANALYSTS,
  'kyc:approve': ['SUPERVISOR'],
  'users:manage': ['ADMIN'],
  'siscoaf:act': ['SUPERVISOR'],
  'rule:write': ['SUPERVISOR', 'ADMIN'],
  'rule:approve': ['SUPERVISOR'],
  'rule:run': ['SUPERVISOR', 'ADMIN'],
}

export function hasAnyRole(userRoles: readonly Role[], allowed: readonly Role[]): boolean {
  return userRoles.some((r) => allowed.includes(r))
}

export function can(userRoles: readonly Role[], action: Action): boolean {
  return hasAnyRole(userRoles, MATRIX[action])
}
