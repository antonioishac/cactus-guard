import { Badge } from '@/components/ui/Badge'
import type { Tone } from '@/components/ui/Badge'
import { ruleStatusLabel } from '../ui-rules'

const TONE: Record<string, Tone> = { ACTIVE: 'success', PENDING_APPROVAL: 'warning', INACTIVE: 'neutral', DRAFT: 'info' }

export function RuleStatusBadge({ status }: { status: string }) {
  return <Badge tone={TONE[status] ?? 'neutral'}>{ruleStatusLabel(status)}</Badge>
}
