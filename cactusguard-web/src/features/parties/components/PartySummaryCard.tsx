import type { Party } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card, KeyValue } from '@/components/ui/Layout'
import { MaskedValue } from '@/components/ui/MaskedValue'
import { formatBRL, formatDate, formatDateTime } from '@/lib/format'
import { useParty } from '../hooks'

const RISK_TONE = { CRITICAL: 'danger', HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' } as const
const PEP_STATUS: Record<string, string> = { ACTIVE: 'PEP ativo', EXPIRED: 'PEP expirado', RELATED: 'Relacionado a PEP', NONE: 'Não PEP' }

function PepBlock({ p }: { p: Party }) {
  if (!p.pep && (!p.pepStatus || p.pepStatus === 'NONE')) return <p className="text-sm text-muted">Sem indicação de PEP.</p>
  return (
    <div className="rounded-lg border border-danger bg-danger-bg p-3">
      <p className="mb-2 text-sm font-semibold text-danger">{PEP_STATUS[p.pepStatus ?? 'ACTIVE'] ?? p.pepStatus}</p>
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <KeyValue label="Cargo / função">{p.pepRole ?? '—'}</KeyValue>
        <KeyValue label="Início no cargo">{formatDate(p.pepStartDate)}</KeyValue>
        <KeyValue label="Fim no cargo">{formatDate(p.pepEndDate)}</KeyValue>
        <KeyValue label="Expira em (fim + 5 anos)">{formatDate(p.pepExpiryDate)}</KeyValue>
      </dl>
    </div>
  )
}

/** Ficha resumida da parte com os campos do catálogo spec 046. PII só mascarada; "revelar" via MaskedValue. */
export function PartySummary({ party }: { party: Party }) {
  const pj = party.type === 'PJ'
  const risk = party.riskLevel ?? '—'
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-base font-semibold">
          {party.name}
        </span>
        <Badge tone="neutral">{pj ? 'Pessoa jurídica' : 'Pessoa física'}</Badge>
        <Badge tone={RISK_TONE[risk as keyof typeof RISK_TONE] ?? 'neutral'}>Risco: {risk}{party.riskScore != null ? ` (${party.riskScore})` : ''}</Badge>
        {party.eddRequired && <Badge tone="warning">EDD necessária</Badge>}
        {party.assetFreezeActive && <Badge tone="danger">Ativos indisponíveis (CSNU)</Badge>}
      </div>

      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
        <KeyValue label={pj ? 'CNPJ' : 'CPF'}>
          <MaskedValue value={party.document} partyId={party.id} field="document" label={pj ? 'CNPJ' : 'CPF'} />
        </KeyValue>
        {!pj && (
          <KeyValue label="Nome da mãe">
            <MaskedValue value={party.motherName} partyId={party.id} field="motherName" label="nome da mãe" />
          </KeyValue>
        )}
        <KeyValue label="E-mail">
          <MaskedValue value={party.email} partyId={party.id} field="email" label="e-mail" />
        </KeyValue>
        <KeyValue label="Telefone">
          <MaskedValue value={party.phone} partyId={party.id} field="phone" label="telefone" />
        </KeyValue>
        <KeyValue label="Situação na Receita">
          {party.receitaStatus ?? '—'}
          {party.receitaCheckedAt && <span className="block text-xs text-muted">verificada em {formatDateTime(party.receitaCheckedAt)}</span>}
        </KeyValue>
        {pj ? (
          <>
            <KeyValue label="Data de abertura">{formatDate(party.incorporationDate)}</KeyValue>
            <KeyValue label="CNAE principal">{party.cnaePrimary ?? '—'}</KeyValue>
            <KeyValue label="Faturamento anual declarado">{formatBRL(party.annualRevenue)}</KeyValue>
            <KeyValue label="Beneficiário final">
              {party.uboIdentified === false ? <Badge tone="danger">Não identificado</Badge> : party.uboIdentified ? 'Identificado' : '—'}
              {party.uboNotIdentifiedReason && <span className="block text-xs text-muted">{party.uboNotIdentifiedReason}</span>}
            </KeyValue>
          </>
        ) : (
          <>
            <KeyValue label="Nascimento">{formatDate(party.birthDate)}</KeyValue>
            <KeyValue label="Ocupação">{party.occupation ?? '—'}</KeyValue>
            <KeyValue label="Renda mensal declarada">{formatBRL(party.declaredMonthlyIncome)}</KeyValue>
            <KeyValue label="Patrimônio declarado">{formatBRL(party.declaredNetWorth)}</KeyValue>
          </>
        )}
        <KeyValue label="Origem dos recursos">{party.sourceOfFunds ?? '—'}</KeyValue>
        <KeyValue label="Propósito do relacionamento">{party.purposeOfRelationship ?? '—'}</KeyValue>
      </dl>

      <div>
        <h3 className="mb-2 text-sm font-semibold">Pessoa exposta politicamente (PEP)</h3>
        <PepBlock p={party} />
      </div>
    </div>
  )
}

export function PartySummaryCard({ partyId }: { partyId: string | null | undefined }) {
  const q = useParty(partyId)
  return (
    <Card title="Dados da parte">
      {!partyId && <p className="text-sm text-muted">Parte não vinculada.</p>}
      {q.isLoading && <Skeleton className="h-24 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} compact />}
      {q.data && <PartySummary party={q.data} />}
    </Card>
  )
}
