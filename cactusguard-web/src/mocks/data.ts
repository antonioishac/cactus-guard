/**
 * Dados em memória do mock (MSW). Valores 100% fictícios; documentos já "mascarados" como a API faria.
 * `resetMockData()` restaura o estado inicial de TODOS os módulos (chamado em afterEach nos testes).
 * O mock faz o papel do backend: calcula `slaStatus`, gera `caseNumber`, etc.
 */
import type {
  AlertComment,
  AlertDetail,
  AlertEvidenceItem,
  AlertHistoryEntry,
  Case,
  CaseEvidence,
  CaseNote,
  Severity,
  SlaStatus,
} from '@/api/types'
import { resetDb2b } from './handlers/db2b'
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

/** Estado do SLA como o backend calcula: análise (45 d da seleção, vencendo <= 5 d) ou comunicação (D+1 útil, vencendo <= 12 h). */
export function computeSla(c: Pick<Case, 'status' | 'analysisDeadline' | 'communicationDeadline'>, now = Date.now()): SlaStatus | null {
  if (['OPEN', 'IN_ANALYSIS', 'ESCALATED', 'PENDING_APPROVAL'].includes(c.status)) {
    if (!c.analysisDeadline) return null
    const left = Date.parse(c.analysisDeadline) - now
    return left < 0 ? 'OVERDUE' : left <= 5 * D ? 'DUE_SOON' : 'ON_TIME'
  }
  if (c.status === 'COMMUNICATE') {
    if (!c.communicationDeadline) return null
    const left = Date.parse(c.communicationDeadline) - now
    return left < 0 ? 'OVERDUE' : left <= 12 * H ? 'DUE_SOON' : 'ON_TIME'
  }
  return null
}

export const caseNumberOf = (n: number) => `CG-${new Date().getFullYear()}-${String(n).padStart(6, '0')}`

const EVIDENCE: AlertEvidenceItem[] = [
  { parameter: 'thresholdValue', label: 'Valor limite por transação', threshold: 'R$ 50.000,00', observed: 'R$ 72.300,00' },
  { parameter: 'periodDays', label: 'Janela de análise', threshold: '30 dias', observed: '3 transações em 6 dias' },
]

function makeAlerts(): AlertDetail[] {
  const severities: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
  const types = ['TRANSACTIONAL', 'PEP', 'SANCTIONS', 'WATCHLIST']
  const rules = ['HIGH_VALUE', 'PEP_MATCH', 'SANCTION_HIT', 'STRUCTURING', 'VELOCITY']
  const statuses: AlertDetail['status'][] = ['OPEN', 'OPEN', 'IN_ANALYSIS', 'OPEN', 'ESCALATED', 'FALSE_POSITIVE']
  return Array.from({ length: 27 }, (_, i): AlertDetail => {
    const pf = i % 2 === 0
    // prazos variados: vencido, vencendo, no prazo
    const selectionOffset = [-2 * D, 2 * D, 20 * D, 40 * D][i % 4]!
    const ruleCode = rules[i % rules.length]!
    const history: AlertHistoryEntry[] = [
      { id: `h-${i}`, action: 'CREATED', fromStatus: null, toStatus: 'OPEN', actorId: null, actorName: 'Motor de regras', justification: null, createdAt: iso(-i * H * 5) },
    ]
    return {
      id: `a-${String(i + 1).padStart(4, '0')}`,
      partyId: pf ? PARTY_PF : PARTY_PJ,
      partyName: pf ? 'João da Silva Santos' : 'Alfa Comércio e Serviços Ltda',
      partyDocument: pf ? '***.456.789-**' : '**.123.456/0001-**',
      type: types[i % types.length]!,
      severity: severities[i % severities.length]!,
      status: statuses[i % statuses.length]!,
      title: `Alerta ${ruleCode} #${i + 1}`,
      description: 'Movimentação incompatível com o perfil declarado da parte.',
      ruleCode,
      ruleVersion: 1 + (i % 3),
      assignedToId: i % 3 === 0 ? 'u-analyst' : null,
      dueDate: iso(5 * D),
      occurredAt: iso(-(i + 1) * D),
      selectionDeadline: iso(selectionOffset),
      totalAmount: 15000 + i * 7350.5,
      falsePositiveReason: null,
      createdAt: iso(-i * H * 5),
      evidence: structuredClone(EVIDENCE),
      evidenceSnapshot: { thresholdValue: 50000, observed: 72300, periodDays: 30 },
      triggeringTransactionIds: [],
      transactions: [],
      history,
      rule: { code: ruleCode, name: `Regra ${ruleCode}`, version: 1 + (i % 3), category: 'TRANSACTIONAL', severity: severities[i % severities.length]!, carta4001Reference: 'Carta Circular 4.001/2020 - movimentação em espécie' },
      party: null,
    }
  })
}

function makeCases(): Case[] {
  const base = (over: Partial<Case> & Pick<Case, 'id'>): Case => ({
    caseNumber: null, alertId: 'a-0001', partyId: PARTY_PJ, partyName: 'Alfa Comércio e Serviços Ltda', partyRiskLevel: 'MEDIUM', status: 'OPEN', priority: 'HIGH',
    assignedToId: 'u-analyst', decision: null, decisionJustification: null, decidedById: null, decidedAt: null, slaDueDate: iso(10 * D), slaStatus: null,
    occurredAt: iso(-20 * D), selectionDeadline: iso(25 * D), selectedAt: iso(-5 * D), analysisDeadline: iso(40 * D), communicationDeadline: null,
    escalationLevel: 0, edd: false, indicatorCodes: ['HIGH_VALUE'], totalAmountInvolved: 120000, approvedById: null, approvedAt: null, closedAt: null, createdAt: iso(-5 * D), ...over,
  })
  const list = [
    base({ id: 'c-0001' }),
    base({
      id: 'c-0002', partyId: PARTY_PF, partyName: 'João da Silva Santos', partyRiskLevel: 'HIGH', alertId: 'a-0002', status: 'PENDING_APPROVAL', edd: true,
      decision: 'COMMUNICATE', decisionJustification: 'Movimentação em espécie incompatível com a renda declarada de agente público.',
      decidedAt: iso(-H * 3), decidedById: 'u-analyst', analysisDeadline: iso(3 * D), priority: 'CRITICAL',
    }),
    base({ id: 'c-0003', status: 'ARCHIVED', decision: 'ARCHIVE', decisionJustification: 'Operações compatíveis com o perfil, documentação suporte anexada.', decidedAt: iso(-8 * D), decidedById: 'u-analyst', closedAt: iso(-8 * D), analysisDeadline: iso(-2 * D), priority: 'LOW' }),
    ...Array.from({ length: 22 }, (_, i) =>
      base({ id: `c-${String(i + 4).padStart(4, '0')}`, priority: (['MEDIUM', 'HIGH', 'LOW'] as const)[i % 3], analysisDeadline: iso(((i % 5) - 1) * 10 * D + D) }),
    ),
  ]
  return list.map((c, i) => ({ ...c, caseNumber: caseNumberOf(i + 1) }))
}

/** Aplica o `slaStatus` calculado (o backend devolve sempre o estado atual). */
export const withSla = (c: Case): Case => ({ ...c, slaStatus: computeSla(c) })

export interface MockDb {
  alerts: AlertDetail[]
  cases: Case[]
  alertComments: Record<string, AlertComment[]>
  notes: Record<string, CaseNote[]>
  evidences: Record<string, CaseEvidence[]>
  /** trilha de auditoria do case (alimenta o dossiê) */
  caseTrail: Record<string, { action: string; userId: string | null; userName: string | null; details: Record<string, unknown> | null; createdAt: string }[]>
  /** acessos revelados (asserções em testes) */
  revealLog: { partyId: string; fields: string[]; reason: string; by: string }[]
  caseSeq: number
}

function initial(): MockDb {
  const cases = makeCases()
  return {
    alerts: makeAlerts(),
    cases,
    alertComments: { 'a-0001': [{ id: 'ac-1', authorId: 'u-analyst', authorName: 'Ana Martins', text: 'Contato com a área comercial para entender a origem dos recursos.', createdAt: iso(-D) }] },
    notes: { 'c-0001': [{ id: 'n-1', authorId: 'u-analyst', authorName: 'Ana Martins', text: 'Solicitada documentação adicional à parte.', createdAt: iso(-D) }] },
    evidences: {
      'c-0001': [
        {
          id: 'e-1', fileName: 'contrato-social.pdf', contentType: 'application/pdf', sizeBytes: 482113, description: 'Contrato social consolidado',
          sha256: 'a3f5c9d1e2b4a6f8c0d2e4f6a8b0c2d4e6f8a0b2c4d6e8f0a2b4c6d8e0f2a4b6', uploadedById: 'u-analyst', uploadedByName: 'Ana Martins', uploadedAt: iso(-2 * D),
        },
      ],
    },
    caseTrail: {
      'c-0002': [
        { action: 'CASE_CREATED', userId: 'u-analyst', userName: 'Ana Martins', details: { alertId: 'a-0002' }, createdAt: iso(-5 * D) },
        { action: 'CASE_DECIDED', userId: 'u-analyst', userName: 'Ana Martins', details: { decision: 'COMMUNICATE' }, createdAt: iso(-3 * H) },
      ],
    },
    revealLog: [],
    caseSeq: cases.length,
  }
}

export const db: MockDb = initial()

/** Restaura TODOS os módulos do mock (core, partes/KYC/screening/usuários e SISCOAF/regras/relatórios/auditoria/notificações). */
export function resetMockData() {
  Object.assign(db, initial())
  resetPldData()
  resetDb2b()
}

/** Valores "reais" devolvidos pela ação de revelar (fictícios). */
export const REVEALED: Record<string, string> = {
  document: '123.456.789-09',
  motherName: 'Maria da Silva Santos',
  email: 'joao.santos@exemplo.com',
  phone: '(11) 98765-1234',
}
