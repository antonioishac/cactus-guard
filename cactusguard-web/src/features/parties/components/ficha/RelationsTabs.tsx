import { Link } from 'react-router'
import { AlertTriangle } from 'lucide-react'
import type { BeneficialOwner, Party, PartyAddress, PartyRelationship, UUID } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Card } from '@/components/ui/Layout'
import { formatDate, formatPercent, maskDocument } from '@/lib/format'
import { useAddresses, useBeneficialOwners, useRelationships } from '../../hooks'
import { effectivePct, isPepActive } from '../../rules'
import { MiniTable, Section } from '../PartySections'

export function AddressesTab({ partyId }: { partyId: UUID }) {
  const q = useAddresses(partyId)
  return (
    <Section<PartyAddress[]>
      title="Endereços"
      q={q}
      isEmpty={(d) => d.length === 0}
      emptyText="Nenhum endereço cadastrado."
      render={(d) => (
        <MiniTable head={['Tipo', 'Endereço', 'Cidade/UF', 'CEP', 'País', 'Principal']}>
          {d.map((a) => (
            <tr key={a.id}>
              <td className="px-2 py-1.5">{a.type ?? '—'}</td>
              <td className="px-2 py-1.5">
                {[a.street, a.number, a.complement, a.neighborhood].filter(Boolean).join(', ') || '—'}
              </td>
              <td className="px-2 py-1.5">{[a.city, a.state].filter(Boolean).join('/') || '—'}</td>
              <td className="px-2 py-1.5 font-mono">{a.zipCode ?? '—'}</td>
              <td className="px-2 py-1.5">{a.country ?? '—'}</td>
              <td className="px-2 py-1.5">{a.primary ? <Badge tone="info">Principal</Badge> : '—'}</td>
            </tr>
          ))}
        </MiniTable>
      )}
    />
  )
}

export function RelationshipsTab({ partyId }: { partyId: UUID }) {
  const q = useRelationships(partyId)
  return (
    <Section<PartyRelationship[]>
      title="Relacionamentos (incluindo relacionados a PEP)"
      q={q}
      isEmpty={(d) => d.length === 0}
      emptyText="Nenhum relacionamento registrado."
      render={(d) => (
        <MiniTable head={['Parte relacionada', 'Tipo de vínculo', 'Descrição', 'Vigência', 'Situação']}>
          {d.map((r) => {
            const rp = r.relatedParty
            const pepRelated = !!rp && isPepActive({ pep: rp.pep, pepStatus: rp.pepStatus })
            return (
              <tr key={r.id}>
                <td className="px-2 py-1.5">
                  {rp ? (
                    <Link to={`/partes/${rp.id}`} className="font-medium text-brand underline-offset-2 hover:underline">
                      {rp.name}
                    </Link>
                  ) : (
                    '—'
                  )}
                  {pepRelated && <span className="ml-1"><Badge tone="danger">PEP</Badge></span>}
                </td>
                <td className="px-2 py-1.5">{r.relationshipType}</td>
                <td className="px-2 py-1.5">{r.description ?? '—'}</td>
                <td className="px-2 py-1.5">
                  {formatDate(r.validFrom)} a {r.validTo ? formatDate(r.validTo) : 'vigente'}
                </td>
                <td className="px-2 py-1.5">{r.active && !r.validTo ? <Badge tone="success">Vigente</Badge> : <Badge tone="neutral">Encerrado</Badge>}</td>
              </tr>
            )
          })}
        </MiniTable>
      )}
    />
  )
}

function OwnerRow({ o }: { o: BeneficialOwner }) {
  const chain = o.ownershipChain ?? []
  const eff = effectivePct(chain.map((c) => c.pct))
  return (
    <li className="rounded-lg border border-line p-3">
      <div className="flex flex-wrap items-center gap-2">
        {o.ownerParty?.id ? (
          <Link to={`/partes/${o.ownerParty.id}`} className="font-medium text-brand underline-offset-2 hover:underline">
            {o.ownerName}
          </Link>
        ) : (
          <span className="font-medium">{o.ownerName}</span>
        )}
        <span className="font-mono text-xs text-muted">{maskDocument(o.ownerDocument)}</span>
        {o.ownerType && <Badge tone="neutral">{o.ownerType}</Badge>}
        {o.finalBeneficiary && <Badge tone="success">Beneficiário final</Badge>}
        {o.pep && <Badge tone="danger">PEP</Badge>}
        <span className="text-sm">
          {o.indirect ? 'Indireto' : 'Direto'}: <strong>{formatPercent(eff ?? o.ownershipPct)}</strong>
        </span>
        {o.controlType && <span className="text-xs text-muted">Controle: {o.controlType}</span>}
      </div>
      {chain.length > 0 && (
        <details className="mt-2 text-sm">
          <summary className="cursor-pointer text-muted">Cadeia societária ({chain.length} elos)</summary>
          <ol className="mt-2 space-y-1 border-l-2 border-line pl-3">
            {chain.map((c, i) => (
              <li key={`${c.name}-${i}`} style={{ marginLeft: `${i * 12}px` }}>
                {c.name} <span className="text-muted">({formatPercent(c.pct)})</span>
              </li>
            ))}
            <li className="font-medium" style={{ marginLeft: `${chain.length * 12}px` }}>
              {o.ownerName} <span className="text-muted">(efetivo {formatPercent(eff)})</span>
            </li>
          </ol>
        </details>
      )}
    </li>
  )
}

/** UBO: lista os beneficiários e a cadeia; alerta quando a PJ não tem beneficiário final identificado. */
export function UboTab({ party }: { party: Party }) {
  const q = useBeneficialOwners(party.id)
  const pj = party.type === 'PJ'
  const owners = q.data ?? []
  const noFinal = q.isSuccess && owners.every((o) => !o.finalBeneficiary)
  const notIdentified = pj && (party.uboIdentified === false || noFinal)
  return (
    <div className="space-y-4">
      {notIdentified && (
        <div role="alert" className="flex items-start gap-3 rounded-lg border border-danger bg-danger-bg p-3 text-danger">
          <AlertTriangle size={20} className="mt-0.5 shrink-0" aria-hidden />
          <div className="text-sm">
            <p className="font-semibold">Beneficiário final não identificado</p>
            <p>{party.uboNotIdentifiedReason ?? 'A cadeia societária não chegou a uma pessoa natural.'} Risco alto e diligência reforçada (EDD).</p>
          </div>
        </div>
      )}
      <Section<BeneficialOwner[]>
        title="Cadeia societária e beneficiários finais (participação a partir de 25%)"
        q={q}
        isEmpty={(d) => d.length === 0}
        emptyText="Nenhum beneficiário registrado."
        render={(d) => (
          <ul className="space-y-2">
            {d.map((o) => (
              <OwnerRow key={o.id} o={o} />
            ))}
          </ul>
        )}
      />
      {!pj && <Card><p className="text-sm text-muted">Para pessoa física o beneficiário final é o próprio titular.</p></Card>}
    </div>
  )
}
