/**
 * Dados em memória do mock para Partes/KYC, Screening, Watchlist e Usuários (etapa 2A). 100% fictícios.
 * CPF/CNPJ gerados com dígito verificador válido e devolvidos já mascarados, como a API faria.
 * NÃO importa `data.ts` (ele chama `resetPldData`); ids de p-0001/p-0002 coincidem com os do mock de alertas/cases.
 */
import type {
  BeneficialOwner,
  KycChecklistItem,
  KycDocument,
  KycProcess,
  Party,
  PartyAddress,
  PartyRelationship,
  RiskHistoryEntry,
  ScreeningResult,
  Transaction,
  UserAccount,
  WatchlistEntry,
} from '@/api/types'
import { maskDocument } from '@/lib/format'

const H = 3_600_000
const D = 24 * H
const iso = (offsetMs: number) => new Date(Date.now() + offsetMs).toISOString()
const day = (offsetMs: number) => iso(offsetMs).slice(0, 10)

/** Gera CPF válido a partir de 9 dígitos-base. */
export function makeCpf(base: string): string {
  const d = base.padStart(9, '0').split('').map(Number)
  for (const len of [9, 10]) {
    const sum = d.slice(0, len).reduce((a, n, i) => a + n * (len + 1 - i), 0)
    d.push(((sum * 10) % 11) % 10)
  }
  return d.join('')
}

/** Gera CNPJ válido (filial 0001) a partir de 8 dígitos-base. */
export function makeCnpj(base: string): string {
  const d = `${base.padStart(8, '0')}0001`.split('').map(Number)
  for (const len of [12, 13]) {
    const w = len === 12 ? [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2] : [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
    const r = d.slice(0, len).reduce((a, n, i) => a + n * w[i]!, 0) % 11
    d.push(r < 2 ? 0 : 11 - r)
  }
  return d.join('')
}

const FIRST = ['Marcos', 'Luciana', 'Rafael', 'Patrícia', 'Gustavo', 'Fernanda', 'Eduardo', 'Camila', 'Thiago', 'Renata', 'Bruno', 'Aline']
const LAST = ['Almeida', 'Ribeiro', 'Carvalho', 'Moreira', 'Teixeira', 'Barbosa', 'Cardoso', 'Nunes', 'Araújo', 'Pinto', 'Freitas', 'Lopes']
const COMPANY = ['Beta Logística', 'Gama Importadora', 'Delta Tecnologia', 'Ômega Alimentos', 'Sigma Construções', 'Kappa Consultoria', 'Lambda Transportes', 'Zeta Atacado']
const RISKS = ['LOW', 'MEDIUM', 'HIGH', 'LOW', 'MEDIUM', 'CRITICAL']

export const PARTY_PF = 'p-0001'
export const PARTY_PJ = 'p-0002'
export const PARTY_DECEASED = 'p-0003'
export const PARTY_FROZEN = 'p-0004'
export const PARTY_RECENT_PJ = 'p-0005'

function baseParty(over: Partial<Party> & Pick<Party, 'id' | 'type' | 'document' | 'name'>): Party {
  return {
    tradeName: null, email: 'c***@e***.com', phone: '(11) *****-0000', riskLevel: 'LOW', birthDate: null, nationality: null, occupation: null,
    status: 'ACTIVE', createdAt: iso(-200 * D), sourceType: 'ONBOARDED', receitaStatus: 'REGULAR', receitaCheckedAt: iso(-5 * D), riskScore: 20,
    eddRequired: false, pep: false, pepStatus: 'NONE', assetFreezeActive: false, ...over,
  }
}

function makeParties(): Party[] {
  const named: Party[] = [
    {
      id: PARTY_PF, type: 'PF', document: '***.456.789-**', name: 'João da Silva Santos', tradeName: null,
      email: 'j***@e***.com', phone: '(11) *****-1234', riskLevel: 'HIGH', birthDate: '1975-03-12', nationality: 'BR',
      occupation: 'Secretário municipal', status: 'ACTIVE', createdAt: iso(-400 * D), sourceType: 'ONBOARDED', countryOfResidence: 'BR', cboCode: '1114-05',
      motherName: 'M*** da S*** S***', receitaStatus: 'REGULAR', receitaCheckedAt: iso(-3 * D),
      declaredMonthlyIncome: 18000, declaredNetWorth: 650000, sourceOfFunds: 'Salário e aluguéis', sourceOfWealth: 'Imóveis herdados',
      purposeOfRelationship: 'Conta corrente pessoal', riskScore: 82, riskCalculatedAt: iso(-1 * D), nextReviewDate: day(60 * D), eddRequired: true,
      pep: true, pepStatus: 'ACTIVE', pepCategory: 'NATIONAL', pepRole: 'Secretário municipal de Obras', pepStartDate: '2021-01-04', pepEndDate: null, pepExpiryDate: null,
      pepApprovalAt: null, assetFreezeActive: false,
    },
    {
      id: PARTY_PJ, type: 'PJ', document: '**.123.456/0001-**', name: 'Alfa Comércio e Serviços Ltda', tradeName: 'Alfa Serviços',
      email: 'c***@a***.com.br', phone: '(21) *****-9876', riskLevel: 'MEDIUM', birthDate: null, nationality: null,
      occupation: null, status: 'ACTIVE', createdAt: iso(-90 * D), sourceType: 'MIGRATED',
      receitaStatus: 'ATIVA', receitaCheckedAt: iso(-10 * D), incorporationDate: new Date(Date.now() - 120 * D).toISOString().slice(0, 10),
      cnaePrimary: '4649-4/99', cnaesSecondary: ['4651-6/01', '7020-4/00'], legalNatureCode: '2062 - Sociedade Empresária Limitada', shareCapital: 50000, companySize: 'EPP',
      nonprofit: false, publiclyTraded: false, annualRevenue: 1200000, sourceOfFunds: 'Receita de vendas', purposeOfRelationship: 'Recebimentos e pagamentos',
      riskScore: 55, riskCalculatedAt: iso(-2 * D), nextReviewDate: day(120 * D), eddRequired: false, uboIdentified: false, uboNotIdentifiedReason: 'Cadeia societária com offshore',
      pep: false, pepStatus: 'NONE',
    },
    baseParty({
      id: PARTY_DECEASED, type: 'PF', document: maskDocument(makeCpf('111444777')), name: 'Antônio Carlos Medeiros', birthDate: '1948-07-02', nationality: 'BR',
      occupation: 'Aposentado', receitaStatus: 'TITULAR_FALECIDO', motherName: 'H*** M***', riskLevel: 'HIGH', riskScore: 74,
      declaredMonthlyIncome: 4200, sourceOfFunds: 'Aposentadoria',
    }),
    baseParty({
      id: PARTY_FROZEN, type: 'PF', document: maskDocument(makeCpf('222555888')), name: 'Viktor Petrov Silva', birthDate: '1981-11-23', nationality: 'RU',
      occupation: 'Empresário', riskLevel: 'CRITICAL', riskScore: 97, eddRequired: true, assetFreezeActive: true, assetFreezeAt: iso(-2 * D),
      assetFreezeReference: 'CSNU Res. 2745/2024', motherName: 'E*** P***',
    }),
    baseParty({
      id: PARTY_RECENT_PJ, type: 'PJ', document: maskDocument(makeCnpj('12345678')), name: 'Sol Nascente Comércio Eireli', tradeName: 'Sol Nascente',
      incorporationDate: day(-70 * D), receitaStatus: 'INAPTA', riskLevel: 'HIGH', riskScore: 78, cnaePrimary: '4789-0/99', companySize: 'ME', annualRevenue: 4800000,
      shareCapital: 5000, uboIdentified: true, eddRequired: true, sourceType: 'MIGRATED',
    }),
  ]
  const generated = Array.from({ length: 21 }, (_, i): Party => {
    const pj = i % 4 === 3
    const n = i + 6
    const id = `p-${String(n).padStart(4, '0')}`
    const risk = RISKS[i % RISKS.length]!
    return baseParty({
      id,
      type: pj ? 'PJ' : 'PF',
      document: pj ? maskDocument(makeCnpj(String(20000000 + i * 137))) : maskDocument(makeCpf(String(300100200 + i * 7919))),
      name: pj ? `${COMPANY[i % COMPANY.length]} Ltda` : `${FIRST[i % FIRST.length]} ${LAST[(i * 5) % LAST.length]}`,
      birthDate: pj ? null : `19${60 + (i % 35)}-0${(i % 9) + 1}-1${i % 9}`,
      nationality: pj ? null : 'BR',
      occupation: pj ? null : ['Engenheiro', 'Professora', 'Comerciante', 'Médico'][i % 4]!,
      incorporationDate: pj ? day(-(900 + i * 40) * D) : undefined,
      cnaePrimary: pj ? '4690-9/00' : undefined,
      riskLevel: risk,
      riskScore: { LOW: 18, MEDIUM: 50, HIGH: 76, CRITICAL: 95 }[risk],
      sourceType: i % 3 === 0 ? 'MIGRATED' : 'ONBOARDED',
      pep: i % 7 === 0,
      pepStatus: i % 7 === 0 ? 'ACTIVE' : 'NONE',
      pepRole: i % 7 === 0 ? 'Vereador' : undefined,
      motherName: pj ? undefined : 'M*** A***',
      declaredMonthlyIncome: pj ? undefined : 6000 + i * 500,
      annualRevenue: pj ? 900000 + i * 10000 : undefined,
      createdAt: iso(-(30 + i * 9) * D),
    })
  })
  return [...named, ...generated]
}

const uboMain: BeneficialOwner[] = [
  { id: 'ubo-1', ownerDocument: '***.111.222-**', ownerName: 'Carlos Eduardo Pires', ownershipPct: 60, controlType: 'PARTICIPACAO', pep: false, finalBeneficiary: true, indirect: false, nationality: 'BR', validFrom: '2024-01-01', validTo: null, ownerType: 'PF', ownerParty: { id: 'p-0006', name: 'Marcos Ribeiro' } },
  {
    id: 'ubo-2', ownerDocument: '***.333.444-**', ownerName: 'Beatriz Nogueira', ownershipPct: 27.5, controlType: 'PARTICIPACAO', pep: true, finalBeneficiary: true, indirect: true, nationality: 'BR', validFrom: '2024-01-01', validTo: null, ownerType: 'PF',
    ownershipChain: [{ name: 'Holding Aurora Ltda', pct: 55 }, { name: 'Offshore Bay Investments Inc.', pct: 50 }],
  },
]

function makeBundle(parties: Party[]) {
  const addresses: Record<string, PartyAddress[]> = {}
  const relationships: Record<string, PartyRelationship[]> = {}
  const ubo: Record<string, BeneficialOwner[]> = {}
  const risk: Record<string, RiskHistoryEntry[]> = {}
  const transactions: Transaction[] = []
  const kyc: KycProcess[] = []
  const checklist: Record<string, KycChecklistItem[]> = {}
  const documents: Record<string, KycDocument[]> = {}

  parties.forEach((p, idx) => {
    addresses[p.id] = [
      { id: `ad-${p.id}-1`, type: 'RESIDENCIAL', street: 'Rua das Acácias', number: String(100 + idx), complement: null, neighborhood: 'Centro', city: 'São Paulo', state: 'SP', zipCode: '01310-100', country: 'BR', primary: true },
      ...(idx % 3 === 0 ? [{ id: `ad-${p.id}-2`, type: 'COMERCIAL', street: 'Av. Paulista', number: '1000', complement: 'Sala 12', neighborhood: 'Bela Vista', city: 'São Paulo', state: 'SP', zipCode: '01310-200', country: 'BR', primary: false }] : []),
    ]
    relationships[p.id] = []
    ubo[p.id] = []
    const lvl = p.riskLevel ?? 'LOW'
    risk[p.id] = [
      { id: `rk-${p.id}-2`, previousScore: 40, newScore: p.riskScore ?? 20, previousLevel: 'MEDIUM', newLevel: lvl, reason: p.pep ? 'Enquadramento como PEP' : 'Recálculo periódico', createdAt: iso(-30 * D) },
      { id: `rk-${p.id}-1`, previousScore: null, newScore: 40, previousLevel: null, newLevel: 'MEDIUM', reason: 'Classificação inicial', createdAt: iso(-150 * D) },
      { id: `rk-${p.id}-3`, previousScore: p.riskScore ?? 20, newScore: p.riskScore ?? 20, previousLevel: lvl, newLevel: lvl, reason: 'Revisão sem alteração', createdAt: iso(-2 * D) },
    ]
    for (let i = 0; i < (idx < 5 ? 14 : 3); i++) {
      transactions.push({
        id: `t-${p.id}-${i}`, partyId: p.id, partyName: p.name, type: ['DEPOSIT', 'PIX', 'TED', 'WITHDRAWAL'][i % 4]!, amount: 4500 + i * 3100, currency: 'BRL',
        counterpartyName: 'Contraparte Exemplo SA', counterpartyDocument: '**.987.654/0001-**', channel: ['AGENCIA', 'APP', 'INTERNET_BANKING'][i % 3]!, status: 'COMPLETED',
        transactionDate: iso(-i * D), originCountry: 'BR', destinationCountry: i % 5 === 0 ? 'KY' : 'BR', purpose: null, direction: i % 2 === 0 ? 'IN' : 'OUT', cash: i % 4 === 0,
        amountBrl: 4500 + i * 3100, createdAt: iso(-i * D), paymentMethod: ['CASH', 'PIX', 'TED', 'CASH'][i % 4], counterpartyCountry: i % 5 === 0 ? 'KY' : 'BR',
      })
    }
    if (idx < 8) {
      const pid = `k-${p.id}`
      kyc.push({ id: pid, partyId: p.id, partyName: p.name, type: p.type === 'PJ' ? 'KYB' : 'KYC', status: idx % 3 === 2 ? 'APPROVED' : 'IN_PROGRESS', riskLevel: p.riskLevel, startedAt: iso(-20 * D), completedAt: idx % 3 === 2 ? iso(-5 * D) : null, expiresAt: iso(340 * D), createdAt: iso(-20 * D) })
      checklist[pid] = [
        { id: `${pid}-c1`, processId: pid, itemCode: 'ID_DOC', itemDescription: 'Documento de identificação', required: true, completed: true, completedAt: iso(-18 * D) },
        { id: `${pid}-c2`, processId: pid, itemCode: 'ADDRESS_PROOF', itemDescription: 'Comprovante de endereço', required: true, completed: idx % 3 === 2, completedAt: idx % 3 === 2 ? iso(-10 * D) : null },
        { id: `${pid}-c3`, processId: pid, itemCode: 'INCOME_PROOF', itemDescription: 'Comprovante de renda / faturamento', required: false, completed: false, completedAt: null },
      ]
      documents[pid] = [{ id: `${pid}-d1`, processId: pid, documentType: p.type === 'PJ' ? 'CONTRATO_SOCIAL' : 'RG', fileName: p.type === 'PJ' ? 'contrato-social.pdf' : 'rg-frente-verso.pdf', status: 'PENDING', createdAt: iso(-18 * D) }]
    }
  })

  // casos específicos
  ubo[PARTY_PJ] = structuredClone(uboMain)
  ubo[PARTY_RECENT_PJ] = [{ id: 'ubo-9', ownerDocument: '***.555.666-**', ownerName: 'Rogério Faria', ownershipPct: 100, controlType: 'PARTICIPACAO', pep: false, finalBeneficiary: true, indirect: false, nationality: 'BR', validFrom: '2025-09-01', validTo: null, ownerType: 'PF' }]
  relationships[PARTY_PF] = [
    { id: 'rel-1', relatedParty: { id: 'p-0007', name: 'Luciana Carvalho', type: 'PF', pep: false, pepStatus: 'RELATED' }, relationshipType: 'CONJUGE', description: 'Cônjuge do PEP', active: true, validFrom: '2021-01-04', validTo: null },
    { id: 'rel-2', relatedParty: { id: PARTY_PJ, name: 'Alfa Comércio e Serviços Ltda', type: 'PJ', pep: false, pepStatus: 'NONE' }, relationshipType: 'SOCIO', description: 'Sócio minoritário', active: false, validFrom: '2019-03-01', validTo: '2022-12-31' },
  ]
  return { addresses, relationships, ubo, risk, transactions, kyc, checklist, documents }
}

function makeHits(): ScreeningResult[] {
  const mk = (id: string, partyId: string, partyName: string, listCode: string, matchedName: string, score: number, type: string, status: string, freeze = false): ScreeningResult => ({
    id, partyId, partyName, listCode, matchedName, matchedDocument: null, matchScore: score, matchType: type, status, createdAt: iso(-3 * D), requiresAssetFreeze: freeze,
  })
  return [
    { ...mk('s-1', PARTY_PF, 'João da Silva Santos', 'PEP', 'João da Silva Santos', 98.5, 'EXACT', 'CONFIRMED'), matchedDocument: '***.456.789-**', reviewJustification: 'Confirmado em fonte oficial (diário do município).', reviewedAt: iso(-29 * D), reviewedByName: 'Ana Martins' },
    mk('s-2', PARTY_PF, 'João da Silva Santos', 'SANCTIONS', 'Joao Silva Santos Jr', 71, 'FUZZY', 'PENDING'),
    mk('s-3', PARTY_FROZEN, 'Viktor Petrov Silva', 'CSNU', 'Viktor Petrov', 93.2, 'FUZZY', 'PENDING', true),
    mk('s-4', 'p-0006', 'Marcos Ribeiro', 'OFAC', 'Marcos Ribeiro Filho', 64, 'FUZZY', 'PENDING'),
    mk('s-5', PARTY_PJ, 'Alfa Comércio e Serviços Ltda', 'GAFI', 'Alfa Trading Co (KY)', 58, 'FUZZY', 'FALSE_POSITIVE'),
    mk('s-6', 'p-0008', 'Patrícia Moreira', 'PEP', 'Patricia Moreira Lima', 82, 'FUZZY', 'PENDING'),
  ]
}

function makeWatchlist(): WatchlistEntry[] {
  return [
    { id: 'w-1', document: maskDocument(makeCpf('900800700')), name: 'Rubens Tavares Neto', reason: 'Fraude documental reincidente em duas instituições parceiras.', severity: 'HIGH', active: true, createdAt: iso(-40 * D), expiresAt: null },
    { id: 'w-2', document: maskDocument(makeCnpj('55667788')), name: 'Fantasma Importações Ltda', reason: 'Empresa de fachada identificada em investigação interna.', severity: 'CRITICAL', active: true, createdAt: iso(-12 * D), expiresAt: iso(300 * D) },
  ]
}

function makeUsers(): UserAccount[] {
  const mk = (id: string, username: string, name: string, roles: string[], active = true): UserAccount => ({ id, username, email: `${username}@cactusguard.exemplo`, name, active, roles, createdAt: iso(-300 * D) })
  return [
    mk('u-analyst', 'analista', 'Ana Martins', ['ANALYST']),
    mk('u-super', 'supervisor', 'Sérgio Lima', ['SUPERVISOR']),
    mk('u-super2', 'supervisor2', 'Sônia Prado', ['SUPERVISOR']),
    mk('u-auditor', 'auditor', 'Augusto Reis', ['AUDITOR']),
    mk('u-admin', 'admin', 'Adriana Costa', ['ADMIN']),
    mk('u-old', 'carlos.antigo', 'Carlos Antigo', ['ANALYST'], false),
  ]
}

export interface PldDb {
  parties: Party[]
  hits: ScreeningResult[]
  watchlist: WatchlistEntry[]
  users: UserAccount[]
  bundle: ReturnType<typeof makeBundle>
  /** log de ações para asserções em testes */
  reviews: { id: string; status: string; justification: string }[]
  pepApprovals: { partyId: string; comment: string; by: string }[]
}

function initial(): PldDb {
  const parties = makeParties()
  return { parties, hits: makeHits(), watchlist: makeWatchlist(), users: makeUsers(), bundle: makeBundle(parties), reviews: [], pepApprovals: [] }
}

export const pld: PldDb = initial()

export function resetPldData() {
  Object.assign(pld, initial())
}
