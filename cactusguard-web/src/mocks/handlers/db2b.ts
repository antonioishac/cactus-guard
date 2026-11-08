/**
 * Dados em memória dos mocks de SISCOAF, regras, relatórios, auditoria e notificações. Tudo fictício.
 * `resetDb2b()` restaura o estado inicial (chamado por `resetMockData()` em afterEach nos testes).
 * Não importa `../data` (que importa este arquivo).
 */
import type {
  AppNotification,
  AuditEntry,
  DataAccessEntry,
  MonitoringRule,
  ReportDefinition,
  ReportJob,
  RuleVersion,
  SiscoafCommunication,
  SiscoafHistoryEntry,
} from '@/api/types'

const H = 3_600_000
const D = 24 * H
const iso = (offsetMs: number) => new Date(Date.now() + offsetMs).toISOString()
const year = new Date().getFullYear()

const U_ANALYST = { id: 'u-analyst', username: 'analista', name: 'Ana Martins' }
const U_SUPER = { id: 'u-super', username: 'supervisor', name: 'Sérgio Lima' }

function comm(over: Partial<SiscoafCommunication> & Pick<SiscoafCommunication, 'id' | 'type' | 'status'>): SiscoafCommunication {
  const c: SiscoafCommunication = {
    caseId: 'c-0002', caseNumber: 'CG-2026-000002', partyId: 'p-0001', partyName: 'João da Silva Santos', transactionId: null, originalCommunicationId: null,
    protocolNumber: null, submittedAt: null, deadlineAt: iso(30 * H), decidedAt: iso(-H), referenceYear: year, amount: 72300.5,
    indicatorCodes: ['CC4001-I.a'], periodFrom: '2026-10-01', periodTo: '2026-10-30', cancelledAt: null, cancelReason: null,
    createdById: 'u-analyst', approvedById: null, approvedAt: null, fourEyesRequired: false, createdAt: iso(-2 * H),
    payload: {
      operationType: 'DEPOSITO_ESPECIE', operationDate: '2026-10-30', counterparty: { name: 'C*** E*** P***', document: '***.111.222-**' },
      indicators: ['CC4001-I.a'], narrative: 'Movimentação em espécie incompatível com a renda declarada.',
    },
    ...over,
  }
  return { ...c, statusHistory: historyFor(c) }
}

/** Histórico mínimo coerente com o status atual (como `siscoaf_history`). */
function historyFor(c: SiscoafCommunication): SiscoafHistoryEntry[] {
  const h: SiscoafHistoryEntry[] = [{ action: 'CREATED', status: 'DRAFT', fromStatus: null, byId: 'u-analyst', byName: 'Ana Martins', reason: null, at: c.createdAt }]
  if (['APPROVED', 'SUBMITTED', 'ACKNOWLEDGED'].includes(c.status)) {
    h.push({ action: 'APPROVED', status: 'APPROVED', fromStatus: 'DRAFT', byId: 'u-super2', byName: 'Sônia Prado', reason: 'Fundamentos conferidos no dossiê do case.', at: iso(-H) })
  }
  if (c.submittedAt) h.push({ action: 'SUBMITTED', status: 'SUBMITTED', fromStatus: 'APPROVED', byId: 'u-super', byName: 'Sérgio Lima', reason: null, at: c.submittedAt })
  if (c.cancelledAt) h.push({ action: 'CANCELLED', status: 'CANCELLED', fromStatus: 'DRAFT', byId: 'u-super', byName: 'Sérgio Lima', reason: c.cancelReason, at: c.cancelledAt })
  return h
}

function makeSiscoaf(): SiscoafCommunication[] {
  return [
    comm({ id: 's-0001-aaaa', type: 'COS', status: 'DRAFT', deadlineAt: iso(30 * H) }),
    comm({ id: 's-0002-aaaa', type: 'COE', status: 'DRAFT', deadlineAt: iso(6 * H), caseId: null, caseNumber: null, amount: 85000, payload: { operationType: 'SAQUE_ESPECIE', amount: 85000 } }),
    comm({ id: 's-0003-aaaa', type: 'COS', status: 'DRAFT', deadlineAt: iso(-5 * H), caseId: 'c-0001', caseNumber: 'CG-2026-000001', partyName: 'Alfa Comércio e Serviços Ltda', partyId: 'p-0002' }),
    comm({ id: 's-0004-aaaa', type: 'COS', status: 'PENDING_REVIEW', deadlineAt: iso(40 * H) }),
    comm({ id: 's-0005-aaaa', type: 'COS', status: 'APPROVED', deadlineAt: iso(20 * H), approvedById: 'u-super2', approvedAt: iso(-H) }),
    comm({ id: 's-0006-aaaa', type: 'COS', status: 'SUBMITTED', protocolNumber: 'COAF-2026-000123', deadlineAt: iso(-3 * D + 5 * H), submittedAt: iso(-3 * D), approvedById: 'u-super2', approvedAt: iso(-3 * D - H) }),
    comm({ id: 's-0007-aaaa', type: 'COE', status: 'SUBMITTED', protocolNumber: 'COAF-2026-000124', deadlineAt: iso(-4 * D), submittedAt: iso(-4 * D + 20 * H), amount: 60000 }),
    comm({ id: 's-0008-aaaa', type: 'COS', status: 'CANCELLED', cancelledAt: iso(-D), cancelReason: 'Case reavaliado e arquivado após nova documentação.', deadlineAt: iso(-D) }),
    comm({ id: 's-0009-aaaa', type: 'COS', status: 'SUBMITTED', protocolNumber: 'COAF-2024-000010', referenceYear: 2024, submittedAt: '2024-05-10T12:00:00.000Z', deadlineAt: '2024-05-11T20:00:00.000Z' }),
    // PEP: quatro olhos exigido; o envio do rascunho exige aprovação prévia
    comm({ id: 's-0010-aaaa', type: 'COS', status: 'DRAFT', fourEyesRequired: true, deadlineAt: iso(28 * H) }),
  ]
}

function snapshot(v: number, threshold: number, window: number, extra: Record<string, unknown> = {}) {
  return { code: 'STRUCTURING', name: 'Indício de fracionamento', severity: 'HIGH', version: v, thresholdValue: threshold, thresholdPeriodDays: window, parameters: { threshold_brl: threshold, window_days: window, min_operations: 3, ...extra } }
}

function rule(over: Partial<MonitoringRule> & Pick<MonitoringRule, 'id' | 'code' | 'name' | 'category' | 'severity' | 'status' | 'version'>): MonitoringRule {
  return {
    description: null, active: over.status === 'ACTIVE', parameters: null, effectiveFrom: iso(-100 * D), effectiveTo: null, thresholdValue: null, thresholdPeriodDays: null,
    createdById: null, createdByName: null, approvedById: null, approvedByName: null, approvedAt: null, carta4001Reference: null, appliesToRiskLevels: null,
    autoCommunication: false, activeVersion: over.status === 'ACTIVE' ? over.version : null, alertsGenerated: null, falsePositiveRate: null, ...over,
  }
}

function makeRules(): MonitoringRule[] {
  return [
    rule({
      id: 'r-cash', code: 'CASH_50K', name: 'Operação em espécie >= R$ 50 mil', description: 'Gera comunicação automática (COE), independentemente de análise de suspeita.',
      category: 'CASH', severity: 'HIGH', status: 'PENDING_APPROVAL', version: 1, effectiveFrom: null, thresholdValue: 50000, autoCommunication: true,
      parameters: { threshold_brl: 50000, communication_type: 'COE', per_operation: true, operations: ['DEPOSIT', 'WITHDRAWAL'] },
      carta4001Reference: 'Circular BCB 3.978/2020 - comunicação de operações em espécie', createdById: 'u-super', createdByName: 'Sérgio Lima', alertsGenerated: 0,
    }),
    rule({
      id: 'r-struct', code: 'STRUCTURING', name: 'Indício de fracionamento', category: 'TRANSACTIONAL', severity: 'HIGH', status: 'ACTIVE', version: 2, thresholdValue: 8000, thresholdPeriodDays: 7,
      parameters: { threshold_brl: 8000, window_days: 7, min_operations: 3, include_related: true }, carta4001Reference: 'Carta Circular 4.001/2020 - fracionamento de depósitos',
      createdById: 'u-super2', createdByName: 'Sônia Prado', approvedById: 'u-super', approvedByName: 'Sérgio Lima', approvedAt: iso(-20 * D), appliesToRiskLevels: ['HIGH', 'CRITICAL'],
      alertsGenerated: 148, falsePositiveRate: 0.34,
    }),
    rule({
      id: 'r-pep', code: 'PEP_MATCH', name: 'Match com lista PEP', category: 'SCREENING', severity: 'CRITICAL', status: 'ACTIVE', version: 1, parameters: { min_score: 85 },
      carta4001Reference: 'Carta Circular 4.001/2020 - PEP', alertsGenerated: 32, falsePositiveRate: 0.12, createdById: 'u-super', createdByName: 'Sérgio Lima', approvedById: 'u-super2', approvedByName: 'Sônia Prado',
    }),
    rule({
      id: 'r-vel', code: 'TXN_VELOCITY', name: 'Velocidade anormal de transações', category: 'TRANSACTIONAL', severity: 'MEDIUM', status: 'INACTIVE', version: 1, effectiveTo: iso(-10 * D),
      parameters: { max_operations: 10, window_hours: 24 }, alertsGenerated: 0, falsePositiveRate: null,
    }),
  ]
}

function makeVersions(): Record<string, RuleVersion[]> {
  const v = (over: Partial<RuleVersion> & Pick<RuleVersion, 'version'>): RuleVersion => ({
    snapshot: null, changeReason: null, approvedById: null, approvedAt: null, effectiveFrom: null, effectiveTo: null, createdById: null, createdByName: null, approvedByName: null, ...over,
  })
  return {
    STRUCTURING: [
      v({ version: 1, snapshot: snapshot(1, 10000, 5), changeReason: 'Versão inicial', approvedById: 'u-super', approvedByName: 'Sérgio Lima', createdByName: 'Sônia Prado', createdById: 'u-super2', approvedAt: iso(-100 * D), effectiveFrom: iso(-100 * D), effectiveTo: iso(-20 * D) }),
      v({ version: 2, snapshot: snapshot(2, 8000, 7, { include_related: true }), changeReason: 'Redução do limiar após revisão trimestral de falsos negativos.', approvedById: 'u-super', approvedByName: 'Sérgio Lima', createdByName: 'Sônia Prado', createdById: 'u-super2', approvedAt: iso(-20 * D), effectiveFrom: iso(-20 * D) }),
    ],
    CASH_50K: [],
    PEP_MATCH: [v({ version: 1, snapshot: { code: 'PEP_MATCH', parameters: { min_score: 85 } }, changeReason: 'Versão inicial', approvedById: 'u-super2', approvedByName: 'Sônia Prado', approvedAt: iso(-100 * D), effectiveFrom: iso(-100 * D) })],
    TXN_VELOCITY: [],
  }
}

function makeDefs(): ReportDefinition[] {
  return [
    { code: 'ALERTS_SUMMARY', name: 'Resumo de Alertas', category: 'OPERATIONAL', description: 'Alertas por status, severidade e período', active: true },
    { code: 'CASES_SLA', name: 'Cases e SLA', category: 'OPERATIONAL', description: 'Cases abertos, vencidos e decididos', active: true },
    { code: 'PEP_EXPOSURE', name: 'Exposição PEP', category: 'COMPLIANCE', description: 'Partes PEP ativas e histórico', active: true },
    { code: 'SCREENING_PENDING', name: 'Screening Pendente', category: 'COMPLIANCE', description: 'Matches pendentes de revisão', active: true },
    { code: 'EFFECTIVENESS', name: 'Relatório de Efetividade', category: 'REGULATORY', description: 'Indicadores de efetividade PLD', active: true },
  ]
}

export interface MockJob extends ReportJob {
  ownerId: string
  /** quantas consultas já avançaram o job (simula processamento assíncrono) */
  polls: number
}

function makeAudit(): AuditEntry[] {
  const e = (over: Partial<AuditEntry> & Pick<AuditEntry, 'id' | 'action' | 'entityType' | 'entityId' | 'createdAt'>): AuditEntry => ({
    oldValue: null, newValue: null, user: U_SUPER, ipAddress: '10.0.0.8', hash: `h-${over.id}`, ...over,
  })
  return [
    e({ id: 'au-r1', action: 'RULE_CREATED', entityType: 'MONITORING_RULE', entityId: 'r-struct', createdAt: iso(-100 * D), newValue: { status: 'PENDING_APPROVAL', threshold_brl: 10000 } }),
    e({
      id: 'au-r2', action: 'RULE_UPDATED', entityType: 'MONITORING_RULE', entityId: 'r-struct', createdAt: iso(-21 * D),
      oldValue: { status: 'ACTIVE', parameters: { threshold_brl: 10000, window_days: 5 } }, newValue: { status: 'PENDING_APPROVAL', parameters: { threshold_brl: 8000, window_days: 7, include_related: true } },
    }),
    e({ id: 'au-s1', action: 'SISCOAF_DRAFT_CREATED', entityType: 'SISCOAF_COMMUNICATION', entityId: 's-0001-aaaa', user: U_ANALYST, createdAt: iso(-2 * H), newValue: { type: 'COS', caseId: 'c-0002' }, ipAddress: '10.0.0.5' }),
    e({ id: 'au-p1', action: 'PARTY_UPDATED', entityType: 'PARTY', entityId: 'p-0001', user: U_ANALYST, createdAt: iso(-3 * D), oldValue: { riskLevel: 'MEDIUM', email: 'j***@e***.com' }, newValue: { riskLevel: 'HIGH', email: 'j***@e***.com' }, ipAddress: '10.0.0.5' }),
    e({ id: 'au-c1', action: 'CASE_CREATED', entityType: 'CASE', entityId: 'c-0002', user: U_ANALYST, createdAt: iso(-5 * D), newValue: { alertId: 'a-0002' }, ipAddress: '10.0.0.5' }),
  ]
}

function makeAccess(): DataAccessEntry[] {
  const a = (id: string, user: { id: string; username: string; name: string }, entityId: string, fields: string, reason: string, createdAt: string): DataAccessEntry => ({
    id, user, entityType: 'PARTY', entityId, fieldsAccessed: fields, accessReason: reason, ipAddress: '10.0.0.5', createdAt,
  })
  return [
    a('da-1', U_ANALYST, 'p-0001', 'document, motherName', 'REVELACAO: análise do case CG-2026-000002', iso(-H)),
    a('da-2', U_SUPER, 'p-0001', 'document', 'REVELACAO: revisão de aprovação', iso(-2 * D)),
    a('da-3', U_ANALYST, 'p-0002', 'email, phone', 'REVELACAO: contato para documentação KYC', iso(-5 * D)),
  ]
}

function makeNotifications(): Record<string, AppNotification[]> {
  const n = (id: string, over: Partial<AppNotification>): AppNotification => ({ id, type: 'INFO', title: '', message: null, read: false, entityType: null, entityId: null, createdAt: iso(-H), ...over })
  return {
    'u-super': [
      n('n-1', { type: 'CASE_PENDING_APPROVAL', title: 'Case aguardando aprovação', message: 'CG-2026-000002 foi decidido e requer o segundo par de olhos.', entityType: 'CASE', entityId: 'c-0002' }),
      n('n-2', { type: 'SISCOAF_DEADLINE', title: 'Prazo de envio ao COAF vencendo', message: 'COE com prazo em poucas horas.', entityType: 'SISCOAF_COMMUNICATION', entityId: 's-0002-aaaa', createdAt: iso(-2 * H) }),
      n('n-3', { type: 'RULE_PENDING', title: 'Regra aguardando aprovação', message: 'CASH_50K v1', entityType: 'MONITORING_RULE', entityId: 'r-cash', read: true, createdAt: iso(-D) }),
    ],
    'u-analyst': [n('n-4', { title: 'Alerta atribuído a você', entityType: 'ALERT', entityId: 'a-0001' })],
  }
}

export interface Db2b {
  siscoaf: SiscoafCommunication[]
  rules: MonitoringRule[]
  versions: Record<string, RuleVersion[]>
  defs: ReportDefinition[]
  jobs: MockJob[]
  audit: AuditEntry[]
  access: DataAccessEntry[]
  notifications: Record<string, AppNotification[]>
  /** sequência de ids para criações (zera junto com os dados) */
  seq: number
}

function initial(): Db2b {
  return { siscoaf: makeSiscoaf(), rules: makeRules(), versions: makeVersions(), defs: makeDefs(), jobs: [], audit: makeAudit(), access: makeAccess(), notifications: makeNotifications(), seq: 100 }
}

export const db2b: Db2b = initial()
export const resetDb2b = () => Object.assign(db2b, initial())
