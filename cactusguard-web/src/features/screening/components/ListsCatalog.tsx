import { Badge } from '@/components/ui/Badge'
import { Card } from '@/components/ui/Layout'
import { formatDate } from '@/lib/format'
import { MiniTable } from '@/features/parties/components/PartySections'
import { useScreeningLists } from '../hooks'
import { ErrorState, Skeleton } from '@/components/ui/Feedback'

export function ListsCatalog() {
  const q = useScreeningLists()
  const data = q.data ?? []
  return (
    <Card title="Listas utilizadas no screening">
      {q.isLoading && <Skeleton className="h-24 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} compact />}
      <MiniTable head={['Código', 'Lista', 'Tipo', 'Fonte', 'Atualizada em', 'Observação']}>
        {data.map((l) => (
          <tr key={l.code}>
            <td className="px-2 py-1.5 font-mono">{l.code}</td>
            <td className="px-2 py-1.5">{l.name}</td>
            <td className="px-2 py-1.5">{l.type}</td>
            <td className="px-2 py-1.5">{l.source ?? '—'}</td>
            <td className="px-2 py-1.5">{formatDate(l.lastUpdated)}</td>
            <td className="px-2 py-1.5">{l.requiresAssetFreeze ? <Badge tone="danger">Hit confirmado bloqueia ativos (Lei 13.810)</Badge> : '—'}</td>
          </tr>
        ))}
      </MiniTable>
    </Card>
  )
}
