import { Link, useParams, useSearchParams } from 'react-router'
import { Pencil } from 'lucide-react'
import { ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Tabs } from '@/components/ui/Tabs'
import type { TabItem } from '@/components/ui/Tabs'
import { useAuth } from '@/features/auth/useAuth'
import { KycTab } from '@/features/kyc/components/KycTab'
import { CadastroTab } from '../components/ficha/CadastroTab'
import { AssetFreezeBanner, FichaHeader } from '../components/ficha/FichaHeader'
import { PartyScreeningTab, PartyTransactionsTab } from '../components/ficha/ActivityTabs'
import { PepTab } from '../components/ficha/PepTab'
import { AddressesTab, RelationshipsTab, UboTab } from '../components/ficha/RelationsTabs'
import { RiskTab } from '../components/ficha/RiskTab'
import { useParty } from '../hooks'
import { isPepActive, isReceitaIrregular } from '../rules'

export function PartyDetailPage() {
  const { id } = useParams()
  const { can } = useAuth()
  const [params, setParams] = useSearchParams()
  const q = useParty(id)

  if (q.isLoading) return <Skeleton className="h-64 w-full" />
  if (q.isError || !q.data) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const party = q.data

  const tabs: TabItem[] = [
    { id: 'cadastro', label: 'Cadastro', alert: isReceitaIrregular(party.receitaStatus) },
    { id: 'enderecos', label: 'Endereços' },
    { id: 'relacionamentos', label: 'Relacionamentos' },
    { id: 'ubo', label: 'UBO', alert: party.type === 'PJ' && party.uboIdentified === false },
    { id: 'pep', label: 'PEP', alert: isPepActive(party) && !party.pepApprovalAt },
    { id: 'risco', label: 'Risco', alert: party.riskLevel === 'HIGH' || party.riskLevel === 'CRITICAL' },
    { id: 'transacoes', label: 'Transações' },
    { id: 'screening', label: 'Screening' },
    { id: 'kyc', label: 'KYC' },
  ]
  const requested = params.get('aba') ?? 'cadastro'
  const active = tabs.some((t) => t.id === requested) ? requested : 'cadastro'

  return (
    <>
      <AssetFreezeBanner party={party} />
      <FichaHeader party={party} />
      {can('party:write') && (
        <div className="mb-3 flex justify-end">
          <Link to={`/partes/${party.id}/editar`} className="inline-flex items-center gap-2 rounded-lg border border-line bg-surface px-3.5 py-2 text-sm font-medium hover:bg-neutral-bg">
            <Pencil size={14} aria-hidden /> Editar cadastro
          </Link>
        </div>
      )}
      <Tabs tabs={tabs} active={active} label="Seções da ficha" onChange={(t) => setParams((p) => { const n = new URLSearchParams(p); n.set('aba', t); return n }, { replace: true })}>
        {active === 'cadastro' && <CadastroTab party={party} />}
        {active === 'enderecos' && <AddressesTab partyId={party.id} />}
        {active === 'relacionamentos' && <RelationshipsTab partyId={party.id} />}
        {active === 'ubo' && <UboTab party={party} />}
        {active === 'pep' && <PepTab party={party} />}
        {active === 'risco' && <RiskTab party={party} />}
        {active === 'transacoes' && <PartyTransactionsTab partyId={party.id} />}
        {active === 'screening' && <PartyScreeningTab party={party} />}
        {active === 'kyc' && <KycTab partyId={party.id} />}
      </Tabs>
    </>
  )
}
