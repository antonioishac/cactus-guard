/**
 * Dados em memória dos mocks da etapa 2B (SISCOAF, regras, relatórios, auditoria, notificações).
 * Tudo fictício. `resetDb2b()` restaura o estado inicial (os testes chamam em beforeEach).
 */
import type { AppNotification, AuditEntry, DataAccessEntry, MonitoringRule, ReportDefinition, ReportJob, RuleVersion, SiscoafCommunication } from '@/api/types'

const H = 3_600_000
const D = 24 * H
const iso = (offsetMs: number) => new Date(Date.now() + offsetMs).toISOString()
const year = new Date().getFullYear()

function comm(over: Partial<SiscoafCommunication> & Pick<SiscoafCommunication, 'id' | 'type' | 'status'>): SiscoafCommunication {
  return {
    caseId: 'c-0002', partyId: 'p-0001', transactionId: null, originalCommunicationId: null, protocolNumber: null, submittedAt: null,
    deadlineAt: iso(30 * H), decidedAt: iso(-H), referenceYear: year, amount: 72300.5, cancelledAt: null, createdAt: iso(-2 * H),
    payload: {
      operationType: 'DEPOSITO_ESPECIE', operationDate: '2026-10-30', counterparty: { name: 'C*** E*** P***', document: '***.111.222-**' },
      indicators: ['CC4001-I.a'], narrative: 'Movimentação em espécie incompatível com a renda declarada.',
    },
    indicatorCodes: ['CC4001-I.a'], partyName: 'João da Silva Santos', periodFrom: '2026-10-01', periodTo: '2026-10-30', ...over,
  }
}

function makeSiscoaf(): SiscoafCommunication[] {
  return [
    comm({ id: 's-0001-aaaa', type: 'COS', status: 'DRAFT', deadlineAt: iso(30 * H) }),
    comm({ id: 's-0002-aaaa', type: 'COE', status: 'DRAFT', deadlineAt: iso(6 * H), caseId: null, amount: 85000, payload: { operationType: 'SAQUE_ESPECIE', amount: 85000 } }),
    comm({ id: 's-0003-aaaa', type: 'COS', status: 'DRAFT', deadlineAt: iso(-5 * H), caseId: 'c-0001' }),
    comm({ id: 's-0004-aaaa', type: 'COS', status: 'PENDING_REVIEW', deadlineAt: iso(40 * H) }),
    comm({ id: 's-0005-aaaa', type: 'COS', status: 'APPROVED', deadlineAt: iso(20 * H) }),
    comm({ id: 's-0006-aaaa', type: 'COS', status: 'SUBMITTED', protocolNumber: 'COAF-2026-000123', deadlineAt: iso(-3 * D + 5 * H), submittedAt: iso(-3 * D) }),
    comm({ id: 's-0007-aaaa', type: 'COE', status: 'SUBMITTED', protocolNumber: 'COAF-2026-000124', deadlineAt: iso(-4 * D), submittedAt: iso(-4 * D + 20 * H), amount: 60000 }),
    comm({ id: 's-0008-aaaa', type: 'COS', status: 'CANCELLED', cancelledAt: iso(-D), cancelReason: 'Case reavaliado e arquivado após nova documentação.', deadlineAt: iso(-D) }),
    comm({ id: 's-0009-aaaa', type: 'COS', status: 'SUBMITTED', protocolNumber: 'COAF-2024-000010', referenceYear: 2024, submittedAt: '2024-05-10T12:00:00.000Z', deadlineAt: '2024-05-11T20:00:00.000Z' }),
  ]
}

function snapshot(v: number, threshold: number, window: number, extra: Record<string, unknown> = {}) {
  return { code: 'STRUCTURING', name: 'Indício de fracionamento', severity: 'HIGH', version: v, thresholdValue: threshold, thresholdPeriodDays: window, parameters: { threshold_brl: threshold, window_days: window, min_operations: 3, ...extra } }
}

function makeRules(): MonitoringRule[] {
  const base = { description: null, active: true, effectiveFrom: iso(-100 * D), effectiveTo: null, thresholdValue: null, thresholdPeriodDays: null }
  return [
    {
      ...base, id: 'r-cash', code: 'CASH_50K', name: 'Operação em espécie >= R$ 50 mil', description: 'Gera comunicação automática (COE), independentemente de análise de suspeita.',
      category: 'CASH', severity: 'HIGH', active: false, status: 'PENDING_APPROVAL', version: 1, effectiveFrom: null, thresholdValue: 50000,
      parameters: { threshold_brl: 50000, communication_type: 'COE', per_operation: true, operations: ['DEPOSIT', 'WITHDRAWAL'] },
      carta4001Reference: 'Circular BCB 3.978/2020 - comunicação de operações em espécie', createdById: 'u-super', createdByName: 'Sérgio Lima',
    },
    {
      ...base, id: 'r-struct', code: 'STRUCTURING', name: 'Indício de fracionamento', category: 'TRANSACTIONAL', severity: 'HIGH', status: 'ACTIVE', version: 2, thresholdValue: 8000, thresholdPeriodDays: 7,
      parameters: { threshold_brl: 8000, window_days: 7, min_operations: 3, include_related: true }, carta4001Reference: 'Carta Circular 4.001/2020 - fracionamento de depósitos',
      createdById: 'u-super2', createdByName: 'Sônia Prado', alertsGenerated: 148, falsePositiveRate: 0.34,
    },
    { ...base, id: 'r-pep', code: 'PEP_MATCH', name: 'Match com lista PEP', category: 'SCREENING', severity: 'CRITICAL', status: 'ACTIVE', version: 1, parameters: { min_score: 85 }, carta4001Reference: 'Carta Circular 4.001/2020 - PEP', alertsGenerated: 32, falsePositiveRate: 0.12 },
    { ...base, id: 'r-vel', code: 'TXN_VELOCITY', name: 'Velocidade anormal de transações', category: 'TRANSACTIONAL', severity: 'MEDIUM', active: false, status: 'INACTIVE', version: 1, effectiveTo: iso(-10 * D), parameters: { max_operations: 10, window_hours: 24 } },
  ]
}

function makeVersions(): Record<string, RuleVersion[]> {
  return {
    STRUCTURING: [
      { version: 1, snapshot: snapshot(1, 10000, 5), changeReason: 'Versão inicial', approvedById: 'u-super', approvedByName: 'Sérgio Lima', createdByName: 'Sônia Prado', createdById: 'u-super2', approvedAt: iso(-100 * D), effectiveFrom: iso(-100 * D), effectiveTo: iso(-20 * D) },
      { version: 2, snapshot: snapshot(2, 8000, 7, { include_related: true }), changeReason: 'Redução do limiar após revisão trimestral de falsos negativos.', approvedById: 'u-super', approvedByName: 'Sérgio Lima', createdByName: 'Sônia Prado', createdById: 'u-super2', approvedAt: iso(-20 * D), effectiveFrom: iso(-20 * D), effectiveTo: null },
    ],
    CASH_50K: [],
    PEP_MATCH: [{ version: 1, snapshot: { code: 'PEP_MATCH', parameters: { min_score: 85 } }, changeReason: 'Versão inicial', approvedById: 'u-super', approvedAt: iso(-100 * D), effectiveFrom: iso(-100 * D), effectiveTo: null }],
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
  const u = { id: 'u-analyst', name: 'Ana Martins' }
  const s = { id: 'u-super', name: 'Sérgio Lima' }
  return [
    { id: 'au-r1', action: 'RULE_CREATED', entityType: 'MONITORING_RULE', entityId: 'r-struct', user: s, createdAt: iso(-100 * D), oldValue: null, newValue: { status: 'PENDING_APPROVAL', threshold_brl: 10000 }, ipAddress: '10.0.0.8' },
    {
      id: 'au-r2', action: 'RULE_UPDATED', entityType: 'MONITORING_RULE', entityId: 'r-struct', user: s, createdAt: iso(-21 * D), ipAddress: '10.0.0.8',
      oldValue: { status: 'ACTIVE', parameters: { threshold_brl: 10000, window_days: 5 } }, newValue: { status: 'PENDING_APPROVAL', parameters: { threshold_brl: 8000, window_days: 7, include_related: true } },
    },
    { id: 'au-s1', action: 'SISCOAF_DRAFT_CREATED', entityType: 'SISCOAF_COMMUNICATION', entityId: 's-0001-aaaa', user: u, createdAt: iso(-2 * H), oldValue: null, newValue: { type: 'COS', caseId: 'c-0002' }, ipAddress: '10.0.0.5' },
    { id: 'au-p1', action: 'PARTY_UPDATED', entityType: 'PARTY', entityId: 'p-0001', user: u, createdAt: iso(-3 * D), oldValue: { riskLevel: 'MEDIUM', email: 'j***@e***.com' }, newValue: { riskLevel: 'HIGH', email: 'j***@e***.com' }, ipAddress: '10.0.0.5' },
  ]
}

function makeAccess(): DataAccessEntry[] {
  return [
    { id: 'da-1', user: { id: 'u-analyst', name: 'Ana Martins' }, entityType: 'PARTY', entityId: 'p-0001', fieldsAccessed: 'document, motherName', accessReason: 'Análise do case CS-2026-0002', ipAddress: '10.0.0.5', createdAt: iso(-H) },
    { id: 'da-2', user: { id: 'u-super', name: 'Sérgio Lima' }, entityType: 'PARTY', entityId: 'p-0001', fieldsAccessed: 'document', accessReason: 'Revisão de aprovação', ipAddress: '10.0.0.8', createdAt: iso(-2 * D) },
    { id: 'da-3', user: { id: 'u-analyst', name: 'Ana Martins' }, entityType: 'PARTY', entityId: 'p-0002', fieldsAccessed: 'email, phone', accessReason: 'Contato para documentação KYC', ipAddress: '10.0.0.5', createdAt: iso(-5 * D) },
  ]
}

function makeNotifications(): Record<string, AppNotification[]> {
  const n = (id: string, over: Partial<AppNotification>): AppNotification => ({ id, type: 'INFO', title: '', message: null, read: false, entityType: null, entityId: null, createdAt: iso(-H), ...over })
  return {
    'u-super': [
      n('n-1', { type: 'CASE_PENDING_APPROVAL', title: 'Case aguardando aprovação', message: 'CS-2026-0002 foi decidido e requer o segundo par de olhos.', entityType: 'CASE', entityId: 'c-0002' }),
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
}

function initial(): Db2b {
  return { siscoaf: makeSiscoaf(), rules: makeRules(), versions: makeVersions(), defs: makeDefs(), jobs: [], audit: makeAudit(), access: makeAccess(), notifications: makeNotifications() }
}

export const db2b: Db2b = initial()
export const resetDb2b = () => Object.assign(db2b, initial())
