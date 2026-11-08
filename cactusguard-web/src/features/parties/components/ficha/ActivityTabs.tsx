import { useState } from 'react'
import { Link } from 'react-router'
import { Play } from 'lucide-react'
import type { Party, ScreeningResult, UUID } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card } from '@/components/ui/Layout'
import { useAuth } from '@/features/auth/useAuth'
import { HitReviewButtons, HitStatusBadge } from '@/features/screening/components/HitReview'
import { DEFAULT_LISTS } from '@/api/screening'
import { useRunScreening } from '@/features/screening/hooks'
import { formatBRL, formatDate, formatDateTime, maskDocument } from '@/lib/format'
import { usePartyTransactionsPage, useScreening } from '../../hooks'
import { directionLabel } from '../../rules'
import { MiniTable } from '../PartySections'

export function PartyTransactionsTab({ partyId }: { partyId: UUID }) {
  const [page, setPage] = useState(0)
  const q = usePartyTransactionsPage(partyId, page)
  const d = q.data
  return (
    <Card title="Transações">
      {q.isLoading && <Skeleton className="h-24 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} compact />}
      {d && d.content.length === 0 && <EmptyState title="Nenhuma transação encontrada." />}
      {d && d.content.length > 0 && (
        <>
          <MiniTable head={['Data', 'Tipo', 'Direção', 'Espécie', 'Meio de pagamento', 'Valor (BRL)', 'Contraparte', 'País']}>
            {d.content.map((t) => (
              <tr key={t.id}>
                <td className="px-2 py-1.5">{formatDateTime(t.transactionDate)}</td>
                <td className="px-2 py-1.5">{t.type}</td>
                <td className="px-2 py-1.5">{directionLabel(t.direction)}</td>
                <td className="px-2 py-1.5">{t.cash ? <Badge tone="warning">Espécie</Badge> : 'Não'}</td>
                <td className="px-2 py-1.5">{t.paymentMethod ?? t.channel ?? '—'}</td>
                <td className="px-2 py-1.5 tabular-nums">{formatBRL(t.amountBrl ?? t.amount)}</td>
                <td className="px-2 py-1.5">
                  {t.counterpartyName ?? '—'} <span className="font-mono text-xs text-muted">{maskDocument(t.counterpartyDocument)}</span>
                </td>
                <td className="px-2 py-1.5">{t.counterpartyCountry ?? t.destinationCountry ?? '—'}</td>
              </tr>
            ))}
          </MiniTable>
          <nav aria-label="Paginação das transações" className="mt-3 flex items-center justify-between text-sm">
            <span className="text-muted">Página {d.number + 1} de {Math.max(d.totalPages, 1)} · {d.totalElements} registros</span>
            <span className="flex gap-1">
              <Button variant="secondary" className="!px-2 !py-1" disabled={d.number <= 0} onClick={() => setPage(page - 1)}>Anterior</Button>
              <Button variant="secondary" className="!px-2 !py-1" disabled={d.number + 1 >= d.totalPages} onClick={() => setPage(page + 1)}>Próxima</Button>
            </span>
          </nav>
        </>
      )}
    </Card>
  )
}

export function PartyScreeningTab({ party }: { party: Party }) {
  const { can } = useAuth()
  const q = useScreening(party.id)
  const run = useRunScreening()
  return (
    <Card
      title="Screening: hits e revisões"
      actions={
        can('screening:run') ? (
          <Button variant="secondary" loading={run.isPending} onClick={() => run.mutate({ partyId: party.id, lists: DEFAULT_LISTS })}>
            <Play size={14} aria-hidden /> Executar screening
          </Button>
        ) : undefined
      }
    >
      {q.isLoading && <Skeleton className="h-16 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} compact />}
      {q.data && q.data.length === 0 && <EmptyState title="Nenhum hit de screening." />}
      {q.data && q.data.length > 0 && (
        <MiniTable head={['Lista', 'Nome encontrado', 'Documento', 'Score', 'Tipo', 'Status', 'Revisão', 'Data', 'Ações']}>
          {q.data.map((s: ScreeningResult) => (
            <tr key={s.id}>
              <td className="px-2 py-1.5">{s.listCode}</td>
              <td className="px-2 py-1.5">{s.matchedName}</td>
              <td className="px-2 py-1.5 font-mono">{maskDocument(s.matchedDocument)}</td>
              <td className="px-2 py-1.5">{s.matchScore}</td>
              <td className="px-2 py-1.5">{s.matchType}</td>
              <td className="px-2 py-1.5"><HitStatusBadge status={s.status} /></td>
              <td className="px-2 py-1.5 text-xs text-muted">
                {s.reviewJustification ?? '—'}
                {s.reviewedAt && <span className="block">{formatDate(s.reviewedAt)}{s.reviewedByName ? ` · ${s.reviewedByName}` : ''}</span>}
              </td>
              <td className="px-2 py-1.5">{formatDate(s.createdAt)}</td>
              <td className="px-2 py-1.5"><HitReviewButtons hit={s} /></td>
            </tr>
          ))}
        </MiniTable>
      )}
      <p className="mt-3 text-xs text-muted">
        <Link to="/screening" className="text-brand underline-offset-2 hover:underline">Ir para a fila de hits pendentes</Link>
      </p>
    </Card>
  )
}
