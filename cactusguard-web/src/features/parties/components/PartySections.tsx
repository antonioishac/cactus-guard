import type { ReactNode } from 'react'
import type { Page, UUID } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card } from '@/components/ui/Layout'
import { formatBRL, formatDate, formatDateTime, formatPercent, maskDocument } from '@/lib/format'
import { directionLabel } from '../rules'
import { useBeneficialOwners, usePartyTransactions, useRiskHistory, useScreening } from '../hooks'

/** Mini-tabela comum às seções do dossiê. */
export function MiniTable({ head, children }: { head: string[]; children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="text-xs uppercase text-muted">
          <tr>{head.map((h) => <th key={h} scope="col" className="px-2 py-1 font-medium">{h}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-line">{children}</tbody>
      </table>
    </div>
  )
}

export interface QLike<T> { isLoading: boolean; isError: boolean; error: unknown; data: T | undefined; refetch: () => unknown }

export function Section<T>({ title, q, isEmpty, emptyText, render }: {
  title: string
  q: QLike<T>
  isEmpty: (d: T) => boolean
  emptyText: string
  render: (d: T) => ReactNode
}) {
  return (
    <Card title={title}>
      {q.isLoading && <Skeleton className="h-16 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} compact />}
      {q.data !== undefined && (isEmpty(q.data) ? <EmptyState title={emptyText} /> : render(q.data))}
    </Card>
  )
}

export function BeneficialOwnersCard({ partyId }: { partyId: UUID | null | undefined }) {
  const q = useBeneficialOwners(partyId)
  return (
    <Section title="Beneficiários finais (UBO)" q={q} isEmpty={(d) => d.length === 0} emptyText="Nenhum beneficiário final registrado." render={(d) => (
      <MiniTable head={['Nome', 'Documento', 'Participação', 'Controle', 'PEP']}>
        {d.map((o) => (
          <tr key={o.id}>
            <td className="px-2 py-1.5">{o.ownerName}{o.indirect && <span className="ml-1 text-xs text-muted">(indireto)</span>}</td>
            <td className="px-2 py-1.5 font-mono">{maskDocument(o.ownerDocument)}</td>
            <td className="px-2 py-1.5">{formatPercent(o.ownershipPct)}</td>
            <td className="px-2 py-1.5">{o.controlType ?? '—'}</td>
            <td className="px-2 py-1.5">{o.pep ? <Badge tone="danger">PEP</Badge> : 'Não'}</td>
          </tr>
        ))}
      </MiniTable>
    )} />
  )
}

export function RiskHistoryCard({ partyId }: { partyId: UUID | null | undefined }) {
  const q = useRiskHistory(partyId)
  return (
    <Section title="Histórico de risco" q={q} isEmpty={(d: Page<unknown>) => d.content.length === 0} emptyText="Sem alterações de risco registradas." render={(d) => (
      <MiniTable head={['Data', 'De', 'Para', 'Score', 'Motivo']}>
        {d.content.map((r) => (
          <tr key={r.id}>
            <td className="px-2 py-1.5">{formatDateTime(r.createdAt)}</td>
            <td className="px-2 py-1.5">{r.previousLevel ?? '—'}</td>
            <td className="px-2 py-1.5 font-medium">{r.newLevel}</td>
            <td className="px-2 py-1.5">{r.newScore}</td>
            <td className="px-2 py-1.5">{r.reason ?? '—'}</td>
          </tr>
        ))}
      </MiniTable>
    )} />
  )
}

export function ScreeningCard({ partyId }: { partyId: UUID | null | undefined }) {
  const q = useScreening(partyId)
  return (
    <Section title="Hits de screening" q={q} isEmpty={(d) => d.length === 0} emptyText="Nenhum hit de screening." render={(d) => (
      <MiniTable head={['Lista', 'Nome encontrado', 'Documento', 'Score', 'Tipo', 'Status', 'Data']}>
        {d.map((s) => (
          <tr key={s.id}>
            <td className="px-2 py-1.5">{s.listCode}</td>
            <td className="px-2 py-1.5">{s.matchedName}</td>
            <td className="px-2 py-1.5 font-mono">{maskDocument(s.matchedDocument)}</td>
            <td className="px-2 py-1.5">{s.matchScore}</td>
            <td className="px-2 py-1.5">{s.matchType}</td>
            <td className="px-2 py-1.5"><Badge tone={s.status === 'PENDING' ? 'warning' : 'neutral'}>{s.status}</Badge></td>
            <td className="px-2 py-1.5">{formatDate(s.createdAt)}</td>
          </tr>
        ))}
      </MiniTable>
    )} />
  )
}

export function TransactionsCard({ partyId, title = 'Transações relevantes' }: { partyId: UUID | null | undefined; title?: string }) {
  const q = usePartyTransactions(partyId)
  return (
    <Section title={title} q={q} isEmpty={(d: Page<unknown>) => d.content.length === 0} emptyText="Nenhuma transação encontrada." render={(d) => (
      <MiniTable head={['Data', 'Tipo', 'Sentido', 'Valor (BRL)', 'Contraparte', 'Canal', 'País destino']}>
        {d.content.map((t) => (
          <tr key={t.id}>
            <td className="px-2 py-1.5">{formatDateTime(t.transactionDate)}</td>
            <td className="px-2 py-1.5">{t.type}{t.cash && <Badge tone="warning"> espécie</Badge>}</td>
            <td className="px-2 py-1.5">{directionLabel(t.direction)}</td>
            <td className="px-2 py-1.5 tabular-nums">{formatBRL(t.amountBrl ?? t.amount)}</td>
            <td className="px-2 py-1.5">{t.counterpartyName ?? '—'} <span className="font-mono text-xs text-muted">{maskDocument(t.counterpartyDocument)}</span></td>
            <td className="px-2 py-1.5">{t.channel ?? '—'}</td>
            <td className="px-2 py-1.5">{t.destinationCountry ?? '—'}</td>
          </tr>
        ))}
      </MiniTable>
    )} />
  )
}
