/**
 * Dados em memória do mock (MSW). Valores 100% fictícios; documentos já "mascarados" como a API faria.
 * `resetMockData()` restaura o estado inicial (usado nos testes).
 */
import type {
  Alert,
  AlertDetail,
  BeneficialOwner,
  Case,
  CaseEvidence,
  CaseNote,
  Party,
  RiskHistoryEntry,
  ScreeningResult,
  Severity,
  Transaction,
} from '@/api/types'

import { resetPldData } from './pldData'

export const MOCK_PASSWORD = 'senha123'

export interface MockUser {
  id: string
  username: string
  name: string
  roles: string[]
}

export const MOCK_USERS: MockUser[] = [
  { id: 'u-analyst', username: 'analista', name: 'Ana Martins', roles: ['ANALYST'] },
  { id: 'u-super', username: 'supervisor', name: 'Sérgio Lima', roles: ['SUPERVISOR'] },
  { id: 'u-super2', username: 'supervisor2', name: 'Sônia Prado', roles: ['SUPERVISOR'] },
  { id: 'u-auditor', username: 'auditor', name: 'Augusto Reis', roles: ['AUDITOR'] },
  { id: 'u-admin', username: 'admin', name: 'Adriana Costa', roles: ['ADMIN'] },
]

const H = 3_600_000
const D = 24 * H
const iso = (offsetMs: number) => new Date(Date.now() + offsetMs).toISOString()

export const PARTY_PF = 'p-0001'
export const PARTY_PJ = 'p-0002'

function makeParties(): Party[] {
  return [
    {
      id: PARTY_PF, type: 'PF', document: '***.456.789-**', name: 'João da Silva Santos', tradeName: null,
      email: 'j***@e***.com', phone: '(11) *****-1234', riskLevel: 'HIGH', birthDate: '1975-03-12', nationality: 'BR',
      occupation: 'Secretário municipal', status: 'ACTIVE', createdAt: iso(-400 * D),
      motherName: 'M*** da S*** S***', receitaStatus: 'REGULAR', receitaCheckedAt: iso(-3 * D),
      declaredMonthlyIncome: 18000, declaredNetWorth: 650000, sourceOfFunds: 'Salário e aluguéis',
      purposeOfRelationship: 'Conta corrente pessoal', riskScore: 82, eddRequired: true,
      pep: true, pepStatus: 'ACTIVE', pepRole: 'Secretário municipal de Obras', pepStartDate: '2021-01-04', pepEndDate: null, pepExpiryDate: null,
    },
    {
      id: PARTY_PJ, type: 'PJ', document: '**.123.456/0001-**', name: 'Alfa Comércio e Serviços Ltda', tradeName: 'Alfa Serviços',
      email: 'c***@a***.com.br', phone: '(21) *****-9876', riskLevel: 'MEDIUM', birthDate: null, nationality: null,
      occupation: null, status: 'ACTIVE', createdAt: iso(-90 * D),
      receitaStatus: 'ATIVA', receitaCheckedAt: iso(-10 * D), incorporationDate: new Date(Date.now() - 120 * D).toISOString().slice(0, 10),
      cnaePrimary: '4649-4/99', annualRevenue: 1200000, sourceOfFunds: 'Receita de vendas', purposeOfRelationship: 'Recebimentos e pagamentos',
      riskScore: 55, eddRequired: false, uboIdentified: false, uboNotIdentifiedReason: 'Cadeia societária com offshore',
      pep: false, pepStatus: 'NONE',
    },
  ]
}

function makeAlerts(): AlertDetail[] {
  const severities: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
  const types = ['TRANSACTIONAL', 'PEP', 'SANCTIONS', 'WATCHLIST']
  const rules = ['HIGH_VALUE', 'PEP_MATCH', 'SANCTION_HIT', 'STRUCTURING', 'VELOCITY']
  const statuses: Alert['status'][] = ['OPEN', 'OPEN', 'IN_ANALYSIS', 'OPEN', 'ESCALATED', 'FALSE_POSITIVE']
  return Array.from({ length: 27 }, (_, i): AlertDetail => {
    const pf = i % 2 === 0
    // prazos variados: vencido, vencendo, no prazo
    const selectionOffset = [-2 * D, 2 * D, 20 * D, 40 * D][i % 4]!
    return {
      id: `a-${String(i + 1).padStart(4, '0')}`,
      partyId: pf ? PARTY_PF : PARTY_PJ,
      partyName: pf ? 'João da Silva Santos' : 'Alfa Comércio e Serviços Ltda',
      type: types[i % types.length]!,
      severity: severities[i % severities.length]!,
      status: statuses[i % statuses.length]!,
      title: `Alerta ${rules[i % rules.length]} #${i + 1}`,
      description: 'Movimentação incompatível com o perfil declarado da parte.',
      ruleCode: rules[i % rules.length]!,
      ruleVersion: 1 + (i % 3),
      assignedToId: i % 3 === 0 ? 'u-analyst' : null,
      dueDate: iso(5 * D),
      occurredAt: iso(-(i + 1) * D),
      selectionDeadline: iso(selectionOffset),
      totalAmount: 15000 + i * 7350.5,
      falsePositiveReason: null,
      createdAt: iso(-i * H * 5),
      cc4001Indicator: 'Grupo I - movimentação em espécie',
      evidence: [
        { parameter: 'thresholdValue', label: 'Valor limite por transação', threshold: 'R$ 50.000,00', observed: 'R$ 72.300,00' },
        { parameter: 'periodDays', label: 'Janela de análise', threshold: '30 dias', observed: '3 transações em 6 dias' },
      ],
      triggeringTransactionIds: ['t-0001', 't-0002'],
      history: [{ id: `h-${i}`, action: 'CREATED', fromStatus: null, toStatus: 'OPEN', actorName: 'Motor de regras', justification: null, createdAt: iso(-i * H * 5) }],
    }
  })
}

function makeCases(): Case[] {
  const base = (over: Partial<Case> & Pick<Case, 'id'>): Case => ({
    alertId: 'a-0001', partyId: PARTY_PJ, partyName: 'Alfa Comércio e Serviços Ltda', status: 'OPEN', priority: 'HIGH',
    assignedToId: 'u-analyst', decision: null, decisionJustification: null, decidedAt: null, slaDueDate: iso(10 * D),
    occurredAt: iso(-20 * D), selectedAt: iso(-5 * D), analysisDeadline: iso(40 * D), communicationDeadline: null,
    escalationLevel: 0, edd: false, approvedById: null, approvedAt: null, closedAt: null, createdAt: iso(-5 * D),
    decidedById: null, indicatorCodes: ['HIGH_VALUE'], caseNumber: null, totalAmountInvolved: 120000, ...over,
  })
  return [
    base({ id: 'c-0001', caseNumber: 'CS-2026-0001' }),
    base({
      id: 'c-0002', caseNumber: 'CS-2026-0002', partyId: PARTY_PF, partyName: 'João da Silva Santos', alertId: 'a-0002', status: 'PENDING_APPROVAL', edd: true,
      decision: 'COMMUNICATE', decisionJustification: 'Movimentação em espécie incompatível com a renda declarada de agente público.',
      decidedAt: iso(-H * 3), decidedById: 'u-analyst', analysisDeadline: iso(3 * D), priority: 'CRITICAL',
    }),
    base({ id: 'c-0003', caseNumber: 'CS-2026-0003', status: 'ARCHIVED', decision: 'ARCHIVE', decisionJustification: 'Operações compatíveis com o perfil, documentação suporte anexada.', decidedAt: iso(-8 * D), decidedById: 'u-analyst', closedAt: iso(-8 * D), analysisDeadline: iso(-2 * D), priority: 'LOW' }),
    ...Array.from({ length: 22 }, (_, i) =>
      base({ id: `c-${String(i + 4).padStart(4, '0')}`, caseNumber: `CS-2026-${String(i + 4).padStart(4, '0')}`, priority: (['MEDIUM', 'HIGH', 'LOW'] as const)[i % 3], analysisDeadline: iso(((i % 5) - 1) * 10 * D + D) }),
    ),
  ]
}

function makeTransactions(): Transaction[] {
  return Array.from({ length: 12 }, (_, i): Transaction => ({
    id: `t-${String(i + 1).padStart(4, '0')}`, partyId: i % 2 === 0 ? PARTY_PF : PARTY_PJ, partyName: null,
    type: ['DEPOSIT', 'PIX', 'TED', 'WITHDRAWAL'][i % 4]!, amount: 9500 + i * 4100, currency: 'BRL',
    counterpartyName: 'Contraparte Exemplo SA', counterpartyDocument: '**.987.654/0001-**', channel: ['AGENCIA', 'APP', 'INTERNET_BANKING'][i % 3]!,
    status: 'COMPLETED', transactionDate: iso(-i * D), originCountry: 'BR', destinationCountry: i % 5 === 0 ? 'KY' : 'BR', purpose: null,
    direction: i % 2 === 0 ? 'CREDIT' : 'DEBIT', cash: i % 4 === 0, amountBrl: 9500 + i * 4100, createdAt: iso(-i * D),
  }))
}

const ubo: BeneficialOwner[] = [
  { id: 'ubo-1', ownerDocument: '***.111.222-**', ownerName: 'Carlos Eduardo Pires', ownershipPct: 60, controlType: 'PARTICIPACAO', pep: false, finalBeneficiary: true, indirect: false, nationality: 'BR', validFrom: '2024-01-01', validTo: null },
  { id: 'ubo-2', ownerDocument: '***.333.444-**', ownerName: 'Beatriz Nogueira', ownershipPct: 27.5, controlType: 'PARTICIPACAO', pep: true, finalBeneficiary: true, indirect: true, nationality: 'BR', validFrom: '2024-01-01', validTo: null },
]
const risk: RiskHistoryEntry[] = [
  { id: 'r-1', previousScore: 55, newScore: 82, previousLevel: 'MEDIUM', newLevel: 'HIGH', reason: 'Enquadramento como PEP', createdAt: iso(-30 * D) },
  { id: 'r-2', previousScore: null, newScore: 55, previousLevel: null, newLevel: 'MEDIUM', reason: 'Classificação inicial', createdAt: iso(-400 * D) },
]
const screening: ScreeningResult[] = [
  { id: 's-1', partyId: PARTY_PF, listCode: 'PEP', matchedName: 'João da Silva Santos', matchedDocument: '***.456.789-**', matchScore: 98.5, matchType: 'EXACT', status: 'CONFIRMED', createdAt: iso(-30 * D) },
  { id: 's-2', partyId: PARTY_PF, listCode: 'SANCTIONS', matchedName: 'Joao Silva Santos Jr', matchedDocument: null, matchScore: 71, matchType: 'FUZZY', status: 'PENDING', createdAt: iso(-2 * D) },
]

export interface MockDb {
  parties: Party[]
  alerts: AlertDetail[]
  cases: Case[]
  transactions: Transaction[]
  ubo: BeneficialOwner[]
  risk: RiskHistoryEntry[]
  screening: ScreeningResult[]
  notes: Record<string, CaseNote[]>
  evidences: Record<string, CaseEvidence[]>
  revealLog: { partyId: string; field: string; reason: string; by: string }[]
}

function initial(): MockDb {
  return {
    parties: makeParties(), alerts: makeAlerts(), cases: makeCases(), transactions: makeTransactions(),
    ubo: structuredClone(ubo), risk: structuredClone(risk), screening: structuredClone(screening),
    notes: { 'c-0001': [{ id: 'n-1', authorName: 'Ana Martins', text: 'Solicitada documentação adicional à parte.', createdAt: iso(-D) }] },
    evidences: { 'c-0001': [{ id: 'e-1', fileName: 'contrato-social.pdf', sizeBytes: 482113, sha256: 'a3f5c9d1e2b4a6f8c0d2e4f6a8b0c2d4e6f8a0b2c4d6e8f0a2b4c6d8e0f2a4b6', uploadedByName: 'Ana Martins', uploadedAt: iso(-2 * D) }] },
    revealLog: [],
  }
}

export const db: MockDb = initial()

export function resetMockData() {
  Object.assign(db, initial())
  resetPldData()
}

/** Valores "reais" devolvidos pela ação de revelar (fictícios). */
export const REVEALED: Record<string, string> = {
  document: '123.456.789-09',
  motherName: 'Maria da Silva Santos',
  email: 'joao.santos@exemplo.com',
  phone: '(11) 98765-1234',
}
