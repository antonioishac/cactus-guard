import { Link } from 'react-router'
import { Ban, ShieldAlert } from 'lucide-react'
import type { Party } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { MaskedValue } from '@/components/ui/MaskedValue'
import { formatDate, formatDateTime } from '@/lib/format'
import { isPepActive, PEP_STATUS_LABEL, RISK_TONE, riskLabel, SOURCE_LABEL } from '../../rules'

/** Faixa vermelha de indisponibilidade de ativos (Lei 13.810/2019, Res. BCB 44/2020). Sem aviso ao cliente. */
export function AssetFreezeBanner({ party }: { party: Party }) {
  if (!party.assetFreezeActive) return null
  return (
    <div role="alert" className="mb-4 flex items-start gap-3 rounded-xl border border-danger bg-danger px-4 py-3 text-white">
      <Ban size={22} className="mt-0.5 shrink-0" aria-hidden />
      <div className="text-sm">
        <p className="text-base font-semibold">Bloqueio de bens ativo (CSNU): ativos indisponíveis</p>
        <p>
          Indisponibilidade imediata e obrigatória (Lei 13.810/2019, Res. BCB 44/2020).
          {party.assetFreezeAt && <> Desde {formatDateTime(party.assetFreezeAt)}.</>}
          {party.assetFreezeReference && <> Referência: {party.assetFreezeReference}.</>}
        </p>
        <p className="font-medium">Não comunicar ao cliente (vedação de ciência, Lei 9.613/98, art. 11, II).</p>
      </div>
    </div>
  )
}

export function FichaHeader({ party }: { party: Party }) {
  const pj = party.type === 'PJ'
  const risk = party.riskLevel ?? ''
  const pep = isPepActive(party)
  return (
    <header className="mb-4 rounded-xl border border-line bg-surface p-4">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="text-xl font-semibold">{party.name}</h1>
        <Badge tone="neutral">{pj ? 'Pessoa jurídica' : 'Pessoa física'}</Badge>
        <Badge tone={RISK_TONE[risk] ?? 'neutral'}>
          Risco: {riskLabel(risk)}
          {party.riskScore != null ? ` (${party.riskScore})` : ''}
        </Badge>
        {pep && (
          <Badge tone="danger">
            <ShieldAlert size={12} className="mr-1" aria-hidden />
            {PEP_STATUS_LABEL[party.pepStatus ?? 'ACTIVE'] ?? 'PEP'}
          </Badge>
        )}
        {party.eddRequired && <Badge tone="warning">EDD necessária</Badge>}
        {party.sourceType && <Badge tone="info">{SOURCE_LABEL[party.sourceType] ?? party.sourceType}</Badge>}
        {party.status !== 'ACTIVE' && <Badge tone="neutral">{party.status}</Badge>}
      </div>
      <p className="mt-2 text-sm text-muted">
        <span className="mr-1">{pj ? 'CNPJ' : 'CPF'}:</span>
        <MaskedValue value={party.document} partyId={party.id} field="document" label={pj ? 'CNPJ' : 'CPF'} />
        <span className="ml-3">Cliente desde {formatDate(party.relationshipStartDate ?? party.createdAt)}</span>
      </p>
      <p className="mt-1 text-xs">
        <Link to="/partes" className="text-brand underline-offset-2 hover:underline">
          Voltar à lista
        </Link>
      </p>
    </header>
  )
}
