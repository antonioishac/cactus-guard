import type { ISODateTime, UUID } from './common'

/** ReportDefinition (entity serializada). */
export interface ReportDefinition {
  id?: UUID
  code: string
  name: string
  description: string | null
  category: string | null
  active: boolean
  createdAt?: ISODateTime
}

export type ReportFormat = 'PDF' | 'EXCEL'
export type ReportJobStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

/** ReportJobResponse do backend (nunca expõe o caminho do arquivo). */
export interface ReportJob {
  id: UUID
  reportCode: string
  status: ReportJobStatus | string
  format: ReportFormat | string
  parameters: Record<string, unknown> | null
  downloadable: boolean
  createdAt: ISODateTime
  completedAt: ISODateTime | null
  /** não existe hoje: mensagem de falha da geração */
  errorMessage?: string | null
}

export interface ReportGenerateRequest {
  format: ReportFormat
  parameters?: Record<string, unknown>
}
