import type { SiscoafCommunication, SiscoafHistoryEntry } from '@/api/types'
import { evaluateDeadline, WARN_WINDOW_MS } from '@/lib/deadline'
import type { DeadlineResult } from '@/lib/deadline'

/** Valor a partir do qual operação em espécie gera COE automática (Circular BCB 3.978 / spec 003). */
export const COE_CASH_THRESHOLD = 50_000

export const SENT_STATUSES = ['SUBMITTED', 'ACKNOWLEDGED']
export const NON_SENDABLE_STATUSES = ['CANCELLED', 'RECTIFIED', 'REJECTED']

/**
 * Semáforo do prazo de envio ao COAF. O prazo (dia útil seguinte) vem pronto do backend em `deadlineAt`;
 * aqui só classificamos: enviada = cumprida (late se depois do prazo); cancelada/retificada/rejeitada = sem prazo;
 * demais comparam com "agora" (janela de alerta de 12 h).
 */
export function siscoafDeadline(c: Pick<SiscoafCommunication, 'deadlineAt' | 'status' | 'submittedAt'>, now = Date.now()): DeadlineResult {
  if (NON_SENDABLE_STATUSES.includes(c.status)) return { state: 'none' }
  const sent = SENT_STATUSES.includes(c.status)
  return evaluateDeadline({
    deadline: c.deadlineAt,
    doneAt: sent ? (c.submittedAt ?? c.deadlineAt) : null,
    warnMs: WARN_WINDOW_MS.communication,
    now,
  })
}

/** COE automática: espécie >= R$ 50 mil, fluxo legal sem análise de suspeita. */
export const isAutomaticCoe = (c: Pick<SiscoafCommunication, 'type'>) => c.type === 'COE'

export const isCancellable = (c: Pick<SiscoafCommunication, 'status'>) => ['DRAFT', 'PENDING_REVIEW', 'APPROVED'].includes(c.status)
export const isApprovable = (c: Pick<SiscoafCommunication, 'status'>) => ['DRAFT', 'PENDING_REVIEW'].includes(c.status)
/** Com quatro olhos exigidos (PEP, risco alto/crítico ou EDD) o rascunho só pode ser enviado após a aprovação. */
export const isSendable = (c: Pick<SiscoafCommunication, 'status' | 'fourEyesRequired'>) =>
  c.status === 'APPROVED' || (c.status === 'DRAFT' && !c.fourEyesRequired)
export const isRectifiable = (c: Pick<SiscoafCommunication, 'status'>) => SENT_STATUSES.includes(c.status)

/** Histórico de status do backend (`siscoaf_history`), do mais antigo para o mais recente. */
export function buildTimeline(c: SiscoafCommunication): SiscoafHistoryEntry[] {
  return [...(c.statusHistory ?? [])].sort((a, b) => a.at.localeCompare(b.at))
}

export const TYPE_LABEL: Record<string, string> = {
  COS: 'COS - operação suspeita',
  SAR: 'COS (legado SAR)',
  COE: 'COE - operação em espécie',
  COAF_COMMUNICATION: 'Comunicação COAF',
  NEGATIVE_DECLARATION: 'Declaração negativa',
}
export const STATUS_LABEL: Record<string, string> = {
  DRAFT: 'Rascunho',
  PENDING_REVIEW: 'Aguardando revisão',
  APPROVED: 'Aprovada',
  SUBMITTED: 'Enviada',
  ACKNOWLEDGED: 'Confirmada pelo COAF',
  REJECTED: 'Rejeitada',
  CANCELLED: 'Cancelada',
  RECTIFIED: 'Retificada',
  DECIDED: 'Decisão registrada',
}
export const typeLabel = (t: string) => TYPE_LABEL[t] ?? t
export const siscoafStatusLabel = (s: string) => STATUS_LABEL[s] ?? s
