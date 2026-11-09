import type { ReportJob } from '@/api/types'

export const REPORT_POLL_MS = 2_000
export const isTerminalJob = (status: string | undefined) => status === 'COMPLETED' || status === 'FAILED'

/** Intervalo de polling do job: continua enquanto PENDING/PROCESSING, para em COMPLETED/FAILED. */
export function jobRefetchInterval(job: Pick<ReportJob, 'status'> | undefined, intervalMs = REPORT_POLL_MS): number | false {
  return isTerminalJob(job?.status) ? false : intervalMs
}

export const JOB_STATUS_LABEL: Record<string, string> = {
  PENDING: 'Na fila',
  PROCESSING: 'Processando',
  COMPLETED: 'Pronto',
  FAILED: 'Falhou',
}
export const jobStatusLabel = (s: string) => JOB_STATUS_LABEL[s] ?? s
