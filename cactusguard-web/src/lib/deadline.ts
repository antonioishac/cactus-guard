/**
 * PROVISÓRIO — classificação visual de prazos regulatórios.
 *
 * Regra de negócio fica no backend (spec 021). Os PRAZOS (selectionDeadline, analysisDeadline,
 * communicationDeadline) já vêm calculados pela API; aqui só comparamos com "agora" para colorir.
 * Quando o backend passar a devolver o estado (ex.: `slaStatus` por prazo), substituir este util
 * pelo valor da API e remover este arquivo.
 */
export type DeadlineState = 'done' | 'ok' | 'warning' | 'overdue' | 'none'

const HOUR = 3_600_000

/** Janela de "vencendo" por etapa (provisória). */
export const WARN_WINDOW_MS = {
  selection: 5 * 24 * HOUR,
  analysis: 5 * 24 * HOUR,
  communication: 12 * HOUR,
} as const

export interface DeadlineInput {
  deadline: string | null | undefined
  /** momento em que a etapa foi cumprida (se já ocorreu) */
  doneAt?: string | null
  warnMs: number
  now?: number
}

export interface DeadlineResult {
  state: DeadlineState
  /** ms restantes (negativo = vencido); undefined se sem prazo */
  remainingMs?: number
  /** cumprida depois do prazo */
  late?: boolean
}

export function evaluateDeadline({ deadline, doneAt, warnMs, now = Date.now() }: DeadlineInput): DeadlineResult {
  if (!deadline) return { state: 'none' }
  const due = new Date(deadline).getTime()
  if (Number.isNaN(due)) return { state: 'none' }
  if (doneAt) {
    const done = new Date(doneAt).getTime()
    return { state: 'done', late: done > due }
  }
  const remainingMs = due - now
  if (remainingMs < 0) return { state: 'overdue', remainingMs }
  if (remainingMs <= warnMs) return { state: 'warning', remainingMs }
  return { state: 'ok', remainingMs }
}

/** Texto curto: "3 d", "5 h", "vencido há 2 d". */
export function formatRemaining(remainingMs: number | undefined): string {
  if (remainingMs === undefined) return ''
  const abs = Math.abs(remainingMs)
  const days = Math.floor(abs / (24 * HOUR))
  const hours = Math.floor(abs / HOUR)
  const txt = days >= 1 ? `${days} d` : hours >= 1 ? `${hours} h` : '< 1 h'
  return remainingMs < 0 ? `vencido há ${txt}` : `faltam ${txt}`
}
