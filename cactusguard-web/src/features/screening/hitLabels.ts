import type { Tone } from '@/components/ui/Badge'

export const HIT_STATUS_LABEL: Record<string, string> = { PENDING: 'Pendente', CONFIRMED: 'Confirmado', FALSE_POSITIVE: 'Falso positivo' }
export const HIT_TONE: Record<string, Tone> = { PENDING: 'warning', CONFIRMED: 'danger', FALSE_POSITIVE: 'neutral' }

