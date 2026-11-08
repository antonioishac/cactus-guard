import { useSearchParams } from 'react-router'
import { PageHeader } from '@/components/ui/Layout'
import { Tabs } from '@/components/ui/Tabs'
import { HitQueue } from '../components/HitQueue'
import { ListsCatalog } from '../components/ListsCatalog'
import { RunScreening } from '../components/RunScreening'
import { WatchlistPanel } from '../components/WatchlistPanel'

const TABS = [
  { id: 'fila', label: 'Fila de hits' },
  { id: 'executar', label: 'Executar screening' },
  { id: 'listas', label: 'Listas' },
  { id: 'interna', label: 'Lista interna (watchlist)' },
]

export function ScreeningPage() {
  const [params, setParams] = useSearchParams()
  const requested = params.get('aba') ?? 'fila'
  const active = TABS.some((t) => t.id === requested) ? requested : 'fila'
  return (
    <>
      <PageHeader title="Screening" subtitle="Hits em listas de PEP, sanções e lista interna: revisão humana com justificativa" />
      <Tabs tabs={TABS} active={active} label="Seções de screening" onChange={(t) => setParams((p) => { const n = new URLSearchParams(p); n.set('aba', t); n.delete('page'); return n }, { replace: true })}>
        {active === 'fila' && <HitQueue />}
        {active === 'executar' && <RunScreening />}
        {active === 'listas' && <ListsCatalog />}
        {active === 'interna' && <WatchlistPanel />}
      </Tabs>
    </>
  )
}
