import type { MonitoringRule, SessionUser } from '@/api/types'

export const RULE_STATUS_LABEL: Record<string, string> = {
  DRAFT: 'Rascunho',
  PENDING_APPROVAL: 'Aguardando aprovação',
  ACTIVE: 'Ativa',
  INACTIVE: 'Inativa',
}
export const ruleStatusLabel = (s: string) => RULE_STATUS_LABEL[s] ?? s

export const CATEGORY_LABEL: Record<string, string> = {
  CASH: 'Espécie',
  PEP: 'PEP',
  SCREENING: 'Screening',
  TRANSACTIONAL: 'Transacional',
  BEHAVIORAL: 'Comportamental',
}
export const categoryLabel = (c: string) => CATEGORY_LABEL[c] ?? c

/** Quatro olhos: quem criou/alterou a regra não a aprova. Só sabemos se o backend enviar `createdById`. */
export const isOwnRule = (r: Pick<MonitoringRule, 'createdById'>, user: SessionUser | null) =>
  !!user && !!r.createdById && r.createdById === user.id

export const isPendingRule = (r: Pick<MonitoringRule, 'status'>) => r.status === 'PENDING_APPROVAL'
export const canDeactivate = (r: Pick<MonitoringRule, 'status'>) => r.status === 'ACTIVE'
/** Ativar/enviar para aprovação: o backend aceita INACTIVE/DRAFT. */
export const canRequestActivation = (r: Pick<MonitoringRule, 'status'>) => ['INACTIVE', 'DRAFT'].includes(r.status)
