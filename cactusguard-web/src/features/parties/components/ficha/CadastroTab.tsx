import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import type { Party } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Card, KeyValue } from '@/components/ui/Layout'
import { MaskedValue } from '@/components/ui/MaskedValue'
import { formatBRL, formatDate, formatDateTime } from '@/lib/format'
import { COMPANY_SIZE_LABEL, isReceitaIrregular, isRecentCompany, receitaLabel } from '../../rules'

const yesNo = (v: boolean | undefined) => (v == null ? '—' : v ? 'Sim' : 'Não')

/** Situação cadastral na Receita, com alerta visual para situações irregulares. */
export function ReceitaPanel({ party }: { party: Party }) {
  const irregular = isReceitaIrregular(party.receitaStatus)
  const pf = party.type !== 'PJ'
  return (
    <Card title="Situação na Receita Federal">
      {!party.receitaStatus ? (
        <p className="text-sm text-muted">Situação cadastral ainda não consultada.</p>
      ) : irregular ? (
        <div role="alert" className="flex items-start gap-3 rounded-lg border border-danger bg-danger-bg p-3 text-danger">
          <AlertTriangle size={20} className="mt-0.5 shrink-0" aria-hidden />
          <div className="text-sm">
            <p className="font-semibold">Situação irregular: {receitaLabel(party.receitaStatus)}</p>
            <p>
              {pf
                ? 'CPF irregular ou de titular falecido em movimentação é forte indício de fraude ou conta de passagem.'
                : 'CNPJ irregular movimentando é indício forte de empresa de fachada.'}
            </p>
          </div>
        </div>
      ) : (
        <p className="flex items-center gap-2 text-sm text-success">
          <CheckCircle2 size={16} aria-hidden /> {receitaLabel(party.receitaStatus)}
        </p>
      )}
      {party.receitaCheckedAt && <p className="mt-2 text-xs text-muted">Consultada em {formatDateTime(party.receitaCheckedAt)}</p>}
    </Card>
  )
}

function PfData({ p }: { p: Party }) {
  return (
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      <KeyValue label="Nome da mãe">
        <MaskedValue value={p.motherName} partyId={p.id} field="motherName" label="nome da mãe" />
      </KeyValue>
      <KeyValue label="Data de nascimento">{formatDate(p.birthDate)}</KeyValue>
      <KeyValue label="Nacionalidade">{p.nationality ?? '—'}</KeyValue>
      <KeyValue label="País de residência">{p.countryOfResidence ?? '—'}</KeyValue>
      <KeyValue label="Ocupação / CBO">
        {p.occupation ?? '—'}
        {p.cboCode && <span className="block text-xs text-muted">CBO {p.cboCode}</span>}
      </KeyValue>
      <KeyValue label="Renda mensal declarada">{formatBRL(p.declaredMonthlyIncome)}</KeyValue>
      <KeyValue label="Patrimônio declarado">{formatBRL(p.declaredNetWorth)}</KeyValue>
      <KeyValue label="Origem dos recursos">{p.sourceOfFunds ?? '—'}</KeyValue>
      <KeyValue label="Origem do patrimônio">{p.sourceOfWealth ?? '—'}</KeyValue>
      <KeyValue label="Propósito do relacionamento">{p.purposeOfRelationship ?? '—'}</KeyValue>
    </dl>
  )
}

function PjData({ p }: { p: Party }) {
  const recent = isRecentCompany(p.incorporationDate)
  return (
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      <KeyValue label="Razão social">{p.name}</KeyValue>
      <KeyValue label="Nome fantasia">{p.tradeName ?? '—'}</KeyValue>
      <KeyValue label="Data de abertura">
        {formatDate(p.incorporationDate)}
        {recent && <Badge tone="warning" title="Aberta há menos de 1 ano">Empresa recente</Badge>}
      </KeyValue>
      <KeyValue label="CNAE principal">{p.cnaePrimary ?? '—'}</KeyValue>
      <KeyValue label="CNAEs secundários">{p.cnaesSecondary?.length ? p.cnaesSecondary.join(', ') : '—'}</KeyValue>
      <KeyValue label="Natureza jurídica">{p.legalNatureCode ?? '—'}</KeyValue>
      <KeyValue label="Capital social">{formatBRL(p.shareCapital)}</KeyValue>
      <KeyValue label="Faturamento anual">{formatBRL(p.annualRevenue)}</KeyValue>
      <KeyValue label="Porte">{p.companySize ? (COMPANY_SIZE_LABEL[p.companySize] ?? p.companySize) : '—'}</KeyValue>
      <KeyValue label="Organização sem fins lucrativos (OSC)">{yesNo(p.nonprofit)}</KeyValue>
      <KeyValue label="Capital aberto">{yesNo(p.publiclyTraded)}</KeyValue>
      <KeyValue label="Origem dos recursos">{p.sourceOfFunds ?? '—'}</KeyValue>
      <KeyValue label="Propósito do relacionamento">{p.purposeOfRelationship ?? '—'}</KeyValue>
    </dl>
  )
}

export function CadastroTab({ party }: { party: Party }) {
  const pj = party.type === 'PJ'
  return (
    <div className="space-y-4">
      <ReceitaPanel party={party} />
      <Card title={pj ? 'Dados da pessoa jurídica' : 'Dados da pessoa física'}>
        {pj ? <PjData p={party} /> : <PfData p={party} />}
        <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-line pt-4 sm:grid-cols-3">
          <KeyValue label="E-mail">
            <MaskedValue value={party.email} partyId={party.id} field="email" label="e-mail" />
          </KeyValue>
          <KeyValue label="Telefone">
            <MaskedValue value={party.phone} partyId={party.id} field="phone" label="telefone" />
          </KeyValue>
          <KeyValue label="Cadastrada em">{formatDateTime(party.createdAt)}</KeyValue>
        </dl>
      </Card>
    </div>
  )
}
