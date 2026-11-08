import { useState } from 'react'
import { Link } from 'react-router'
import { Play, Search } from 'lucide-react'
import type { Party } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Layout'
import { useAuth } from '@/features/auth/useAuth'
import { useParties } from '@/features/parties/hooks'
import { maskDocument } from '@/lib/format'
import { HitReviewButtons, HitStatusBadge } from './HitReview'
import { useRunScreening, useScreeningLists } from '../hooks'
import { FALLBACK_LISTS } from '../lists'

/** Execução manual: escolhe a parte (busca por nome/documento, só em memória), as listas e executa. */
export function RunScreening() {
  const { can } = useAuth()
  const lists = useScreeningLists()
  const catalog = lists.data?.filter((l) => l.active) ?? FALLBACK_LISTS
  const [term, setTerm] = useState('')
  const [submitted, setSubmitted] = useState('')
  const [party, setParty] = useState<Party | null>(null)
  const [selected, setSelected] = useState<string[]>(['PEP', 'SANCTIONS'])
  const found = useParties({ q: submitted || undefined }, { size: 8 })
  const run = useRunScreening()

  if (!can('screening:run')) return <Card><p className="text-sm text-muted">Seu perfil não pode executar screening manual.</p></Card>

  const toggle = (code: string) => setSelected((s) => (s.includes(code) ? s.filter((c) => c !== code) : [...s, code]))

  return (
    <div className="space-y-4">
      <Card title="1. Escolha a parte">
        <form className="flex flex-wrap items-end gap-2" onSubmit={(e) => { e.preventDefault(); setSubmitted(term.trim()) }}>
          <div className="min-w-60 flex-1">
            <label htmlFor="scr-search" className="mb-1 block text-xs font-medium text-muted">Nome ou documento</label>
            <input id="scr-search" value={term} onChange={(e) => setTerm(e.target.value)} autoComplete="off" className="w-full rounded-lg border border-line px-3 py-2 text-sm" />
          </div>
          <Button type="submit" variant="secondary" disabled={!term.trim()}><Search size={14} aria-hidden /> Buscar</Button>
        </form>
        {submitted && found.data && (
          <ul className="mt-3 divide-y divide-line rounded-lg border border-line text-sm" aria-label="Resultados da busca">
            {found.data.content.length === 0 && <li className="p-3 text-muted">Nenhuma parte encontrada.</li>}
            {found.data.content.map((p) => (
              <li key={p.id}>
                <label className="flex cursor-pointer items-center gap-3 p-2.5 hover:bg-canvas">
                  <input type="radio" name="scr-party" checked={party?.id === p.id} onChange={() => setParty(p)} />
                  <span className="font-medium">{p.name}</span>
                  <span className="font-mono text-xs text-muted">{maskDocument(p.document)}</span>
                </label>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card title="2. Listas">
        <fieldset>
          <legend className="sr-only">Listas de screening</legend>
          <div className="grid gap-2 sm:grid-cols-2">
            {catalog.map((l) => (
              <label key={l.code} className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={selected.includes(l.code)} onChange={() => toggle(l.code)} className="h-4 w-4" />
                {l.name} <span className="text-xs text-muted">({l.code})</span>
              </label>
            ))}
          </div>
        </fieldset>
        <div className="mt-4 flex items-center gap-3">
          <Button loading={run.isPending} disabled={!party || selected.length === 0} onClick={() => party && run.mutate({ partyId: party.id, lists: selected })}>
            <Play size={14} aria-hidden /> Executar screening{party ? ` para ${party.name}` : ''}
          </Button>
        </div>
      </Card>

      {run.data && (
        <Card title="Resultado">
          <p className="flex flex-wrap items-center gap-2 text-sm">
            <HitStatusBadge status={run.data.status} />
            Lista {run.data.listCode}: {run.data.matchedName} (score {run.data.matchScore})
            <HitReviewButtons hit={run.data} />
            <Link to={`/partes/${run.data.partyId}?aba=screening`} className="text-brand underline-offset-2 hover:underline">Abrir ficha</Link>
          </p>
        </Card>
      )}
    </div>
  )
}
