import type { ReactNode } from 'react'
import { severityLabel, statusLabel } from '@/lib/format'

export type Tone = 'danger' | 'orange' | 'warning' | 'success' | 'info' | 'neutral'

const TONE: Record<Tone, string> = {
  danger: 'bg-danger-bg text-danger',
  orange: 'bg-orange-bg text-orange',
  warning: 'bg-warning-bg text-warning',
  success: 'bg-success-bg text-success',
  info: 'bg-info-bg text-info',
  neutral: 'bg-neutral-bg text-neutral',
}

export function Badge({ tone = 'neutral', children, title }: { tone?: Tone; children: ReactNode; title?: string }) {
  return (
    <span title={title} className={`inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ${TONE[tone]}`}>
      {children}
    </span>
  )
}

const SEVERITY_TONE: Record<string, Tone> = { CRITICAL: 'danger', HIGH: 'orange', MEDIUM: 'warning', LOW: 'success' }

export function SeverityBadge({ severity }: { severity: string }) {
  return <Badge tone={SEVERITY_TONE[severity] ?? 'neutral'}>{severityLabel(severity)}</Badge>
}

const STATUS_TONE: Record<string, Tone> = {
  OPEN: 'info',
  IN_ANALYSIS: 'info',
  ESCALATED: 'orange',
  PENDING_APPROVAL: 'warning',
  FALSE_POSITIVE: 'neutral',
  CLOSED: 'neutral',
  ARCHIVED: 'neutral',
  COMMUNICATE: 'danger',
}

export function StatusBadge({ status }: { status: string }) {
  return <Badge tone={STATUS_TONE[status] ?? 'neutral'}>{statusLabel(status)}</Badge>
}
