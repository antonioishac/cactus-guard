import { http, HttpResponse } from 'msw'
import type {
  Alert,
  AlertDetail,
  AuthResponse,
  Case,
  CaseDossier,
  CaseEvidence,
  CaseNote,
  PendingApprovals,
  TransactionSummary,
} from '@/api/types'
import { caseNumberOf, computeSla, db, MOCK_PASSWORD, MOCK_USERS, withSla } from './data'
import type { MockUser } from './data'
import { handlers2B } from './handlers/all2b'
import { db2b } from './handlers/db2b'
import {
  ANALYSTS,
  API,
  JUSTIFICATION_MSG,
  SUP,
  err,
  guard,
  invalid,
  isResponse,
  jsonBody,
  multi,
  paginate,
  parseDay,
  readMultipart,
  validJustification,
} from './handlers/helpers'
import { pldHandlers } from './handlers/pld'
import { pld } from './pldData'

const D = 86_400_000
const MAX_EVIDENCE_BYTES = 10 * 1024 * 1024
const CLOSED_CASE = ['ARCHIVED', 'COMMUNICATED']

let tokenSeq = 0
function issue(u: MockUser): AuthResponse {
  tokenSeq += 1
  return {
    accessToken: `acc.${u.id}.${tokenSeq}`,
    refreshToken: `ref.${u.id}.${tokenSeq}`,
    expiresIn: 1800,
    user: { id: u.id, username: u.username, name: u.name, roles: u.roles },
  }
}

const findCase = (id: unknown) => db.cases.find((c) => c.id === id)
const findAlert = (id: unknown) => db.alerts.find((a) => a.id === id)
const partyOf = (id: string | null) => pld.parties.find((p) => p.id === id)

/** Remove os campos exclusivos do detalhe: `AlertResponse`. */
function toAlertResponse(a: AlertDetail): Alert {
  /* eslint-disable @typescript-eslint/no-unused-vars */
  const { evidence, evidenceSnapshot, triggeringTransactionIds, transactions, history, rule, party, ...list } = a
  /* eslint-enable @typescript-eslint/no-unused-vars */
  return list
}

function txSummaries(partyId: string | null, limit: number): TransactionSummary[] {
  return pld.bundle.transactions
    .filter((t) => t.partyId === partyId)
    .slice(0, limit)
    .map((t) => ({
      id: t.id, type: t.type, amount: t.amount, currency: t.currency, direction: t.direction, transactionDate: t.transactionDate, channel: t.channel, cash: t.cash,
      paymentMethod: t.paymentMethod, counterpartyName: t.counterpartyName, counterpartyDocument: t.counterpartyDocument, counterpartyCountry: t.counterpartyCountry, status: t.status,
    }))
}

function partySummary(partyId: string | null) {
  const p = partyOf(partyId)
  if (!p) return null
  return {
    id: p.id, name: p.name, type: p.type, document: p.document, riskLevel: p.riskLevel, riskScore: p.riskScore ?? null, pep: !!p.pep, pepStatus: p.pepStatus ?? null,
    eddRequired: !!p.eddRequired, assetFreezeActive: !!p.assetFreezeActive, status: p.status,
  }
}

function toAlertDetail(a: AlertDetail): AlertDetail {
  const txs = txSummaries(a.partyId, 2)
  return { ...a, transactions: txs, triggeringTransactionIds: txs.map((t) => t.id), party: partySummary(a.partyId) }
}

function trail(caseId: string, user: MockUser | null, action: string, details: Record<string, unknown> | null = null) {
  ;(db.caseTrail[caseId] ??= []).push({ action, userId: user?.id ?? null, userName: user?.name ?? null, details, createdAt: new Date().toISOString() })
}

/** Cria o case a partir do alerta (POST /cases e alias legado). */
function openCase(u: MockUser, alertId: unknown, justification: unknown, requireJustification: boolean): Response {
  const a = findAlert(alertId)
  if (!a) return err(404, 'Alerta não encontrado')
  if (requireJustification && !validJustification(justification)) return err(422, JUSTIFICATION_MSG)
  if (['FALSE_POSITIVE', 'CLOSED'].includes(a.status)) return err(422, `Alerta em status ${a.status} não pode ser selecionado`)
  if (db.cases.some((c) => c.alertId === a.id)) return err(422, 'Já existe case para este alerta')
  const p = partyOf(a.partyId)
  const now = Date.now()
  db.caseSeq += 1
  const c: Case = {
    id: `c-${String(db.cases.length + 1).padStart(4, '0')}`, caseNumber: caseNumberOf(db.caseSeq), alertId: a.id, partyId: a.partyId, partyName: a.partyName,
    partyRiskLevel: p?.riskLevel ?? null, status: 'OPEN', priority: a.severity, assignedToId: u.id, decision: null, decisionJustification: null, decidedById: null, decidedAt: null,
    slaDueDate: null, slaStatus: null, occurredAt: a.occurredAt, selectionDeadline: a.selectionDeadline, selectedAt: new Date(now).toISOString(),
    analysisDeadline: new Date(now + 45 * D).toISOString(), communicationDeadline: null, escalationLevel: 0, edd: !!p?.eddRequired, indicatorCodes: a.ruleCode ? [a.ruleCode] : [],
    totalAmountInvolved: a.totalAmount, approvedById: null, approvedAt: null, closedAt: null, createdAt: new Date(now).toISOString(),
  }
  db.cases.unshift(c)
  a.status = 'IN_ANALYSIS'
  a.history = [
    ...(a.history ?? []),
    { id: `h-${Date.now()}`, action: 'CASE_OPENED', fromStatus: null, toStatus: 'IN_ANALYSIS', actorId: u.id, actorName: u.name, justification: typeof justification === 'string' ? justification : null, createdAt: new Date().toISOString() },
  ]
  trail(c.id, u, 'CASE_CREATED', { alertId: a.id })
  return HttpResponse.json(withSla(c), { status: 201 })
}

export const handlers = [
  // SISCOAF, regras, relatórios, auditoria e notificações
  ...handlers2B,
  // partes/KYC, screening, watchlist, usuários
  ...pldHandlers,

  /* ---------- auth ---------- */
  http.post(`${API}/auth/login`, async ({ request }) => {
    const body = await jsonBody<{ username: string; password: string }>(request)
    const u = MOCK_USERS.find((x) => x.username === body.username)
    if (!u || body.password !== MOCK_PASSWORD) return err(401, 'Credenciais inválidas')
    return HttpResponse.json(issue(u))
  }),
  http.post(`${API}/auth/refresh`, async ({ request }) => {
    const body = await jsonBody<{ refreshToken: string }>(request)
    const id = /^ref\.([^.]+)\./.exec(body.refreshToken ?? '')?.[1]
    const u = MOCK_USERS.find((x) => x.id === id)
    return u ? HttpResponse.json(issue(u)) : err(401, 'Refresh token inválido')
  }),
  http.post(`${API}/auth/logout`, () => new HttpResponse(null, { status: 204 })),

  /* ---------- dashboard ---------- */
  http.get(`${API}/dashboard/operational`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const open = db.alerts.filter((a) => a.status === 'OPEN')
    const openCases = db.cases.filter((c) => c.status === 'OPEN').length
    return HttpResponse.json({
      openAlerts: open.length, openCases, pendingKyc: pld.bundle.kyc.filter((k) => k.status === 'IN_PROGRESS').length,
      criticalAlerts: open.filter((a) => a.severity === 'CRITICAL').length, alertCaseRatio: open.length ? openCases / open.length : 0,
    })
  }),
  http.get(`${API}/dashboard/metrics`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const byStatus: Record<string, number> = { OPEN: 0, IN_ANALYSIS: 0, ESCALATED: 0, FALSE_POSITIVE: 0, CLOSED: 0 }
    db.alerts.forEach((a) => (byStatus[a.status] = (byStatus[a.status] ?? 0) + 1))
    return HttpResponse.json({
      alertsByStatus: byStatus, screenings: pld.hits.filter((h) => h.status === 'PENDING').length, activePeps: pld.parties.filter((p) => p.pep && p.pepStatus === 'ACTIVE').length,
      openCases: db.cases.filter((c) => !CLOSED_CASE.includes(c.status)).length, pendingApproval: db.cases.filter((c) => c.status === 'PENDING_APPROVAL').length,
    })
  }),
  http.get(`${API}/dashboard/alerts-chart`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const days = Math.min(90, Math.max(1, Number(new URL(request.url).searchParams.get('days') ?? 7) || 7))
    return HttpResponse.json(Array.from({ length: days }, (_, i) => ({ date: new Date(Date.now() - (days - 1 - i) * D).toISOString().slice(0, 10), total: 4 + ((i * 5) % 9) })))
  }),
  http.get(`${API}/dashboard/severity-distribution`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const active = db.alerts.filter((a) => ['OPEN', 'IN_ANALYSIS', 'ESCALATED'].includes(a.status))
    return HttpResponse.json((['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const).map((s) => ({ severity: s, count: active.filter((a) => a.severity === s).length })))
  }),
  http.get(`${API}/dashboard/case-sla`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const bucket = (states: (string | null)[]) => ({
      onTime: states.filter((s) => s === 'ON_TIME').length, dueSoon: states.filter((s) => s === 'DUE_SOON').length, overdue: states.filter((s) => s === 'OVERDUE').length,
    })
    const analysis = db.cases.filter((c) => ['OPEN', 'IN_ANALYSIS', 'ESCALATED', 'PENDING_APPROVAL'].includes(c.status)).map((c) => computeSla(c))
    const communication = db.cases.filter((c) => c.status === 'COMMUNICATE').map((c) => computeSla(c))
    const selection = db.alerts
      .filter((a) => a.status === 'OPEN' && a.selectionDeadline)
      .map((a) => {
        const left = Date.parse(a.selectionDeadline!) - Date.now()
        return left < 0 ? 'OVERDUE' : left <= 5 * D ? 'DUE_SOON' : 'ON_TIME'
      })
    return HttpResponse.json({ selection: bucket(selection), analysis: bucket(analysis), communication: bucket(communication), pendingApproval: db.cases.filter((c) => c.status === 'PENDING_APPROVAL').length })
  }),

  /* ---------- fila de aprovação (SUPERVISOR) ---------- */
  http.get(`${API}/approvals/pending`, ({ request }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const url = new URL(request.url)
    const cases = db.cases
      .filter((c) => c.status === 'PENDING_APPROVAL')
      .sort((a, b) => (a.decidedAt ?? '').localeCompare(b.decidedAt ?? ''))
      .map((c) => ({
        id: c.id, caseNumber: c.caseNumber, partyId: c.partyId, partyName: c.partyName, partyRiskLevel: c.partyRiskLevel, decision: c.decision, decidedById: c.decidedById,
        decidedAt: c.decidedAt, edd: c.edd, slaStatus: computeSla(c), analysisDeadline: c.analysisDeadline, ownDecision: c.decidedById === u.id,
      }))
    const rules = db2b.rules
      .filter((r) => r.status === 'PENDING_APPROVAL')
      .map((r) => ({ code: r.code, name: r.name, category: r.category, severity: r.severity, version: r.version, createdById: r.createdById, createdByName: r.createdByName, updatedAt: r.effectiveFrom ?? new Date().toISOString(), ownChange: r.createdById === u.id }))
    const peps = pld.parties
      .filter((p) => p.pep && p.pepStatus === 'ACTIVE' && !p.pepApprovalAt)
      .map((p) => ({ partyId: p.id, partyName: p.name, document: p.document, pepCategory: p.pepCategory ?? null, pepRole: p.pepRole ?? null, pepStartDate: p.pepStartDate ?? null, riskLevel: p.riskLevel, markedAt: p.riskCalculatedAt ?? null }))
    const body: PendingApprovals = { cases: paginate(cases, url), rules: paginate(rules, url), peps: paginate(peps, url), counts: { cases: cases.length, rules: rules.length, peps: peps.length } }
    return HttpResponse.json(body)
  }),

  /* ---------- alertas ---------- */
  http.get(`${API}/alerts`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const url = new URL(request.url)
    const p = url.searchParams
    const from = parseDay(p.get('from'), false)
    const to = parseDay(p.get('to'), true)
    if (from === null || to === null) return err(422, 'Período inválido: use AAAA-MM-DD ou ISO-8601')
    const status = multi(url, 'status')
    const severity = multi(url, 'severity')
    let items = db.alerts
    if (status.length) items = items.filter((a) => status.includes(a.status))
    if (severity.length) items = items.filter((a) => severity.includes(a.severity))
    if (p.get('type')) items = items.filter((a) => a.type === p.get('type'))
    if (p.get('assignedTo')) items = items.filter((a) => a.assignedToId === p.get('assignedTo'))
    if (p.get('partyId')) items = items.filter((a) => a.partyId === p.get('partyId'))
    if (p.get('ruleCode')) items = items.filter((a) => a.ruleCode === p.get('ruleCode'))
    if (from !== undefined) items = items.filter((a) => Date.parse(a.createdAt) >= from)
    if (to !== undefined) items = items.filter((a) => Date.parse(a.createdAt) <= to)
    return HttpResponse.json(paginate(items.map(toAlertResponse), url))
  }),
  http.get(`${API}/alerts/:id/comments`, ({ request, params }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    if (!findAlert(params.id)) return err(404, 'Alerta não encontrado')
    return HttpResponse.json(db.alertComments[String(params.id)] ?? [])
  }),
  http.post(`${API}/alerts/:id/comments`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const a = findAlert(params.id)
    if (!a) return err(404, 'Alerta não encontrado')
    const body = await jsonBody<{ text: string }>(request)
    if (!body.text?.trim()) return invalid('Dados inválidos', [{ field: 'text', message: 'must not be blank' }])
    if (body.text.length > 5000) return invalid('Dados inválidos', [{ field: 'text', message: 'size must be between 0 and 5000' }])
    const c = { id: `ac-${Date.now()}`, authorId: u.id, authorName: u.name, text: body.text.trim(), createdAt: new Date().toISOString() }
    ;(db.alertComments[a.id] ??= []).push(c)
    a.history = [...(a.history ?? []), { id: `h-${Date.now()}`, action: 'COMMENT', fromStatus: null, toStatus: null, actorId: u.id, actorName: u.name, justification: null, createdAt: c.createdAt }]
    return HttpResponse.json(c, { status: 201 })
  }),
  http.get(`${API}/alerts/:id`, ({ request, params }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const a = findAlert(params.id)
    return a ? HttpResponse.json(toAlertDetail(a)) : err(404, 'Alerta não encontrado')
  }),
  http.post(`${API}/alerts/:id/assume`, ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const a = findAlert(params.id)
    if (!a) return err(404, 'Alerta não encontrado')
    a.status = 'IN_ANALYSIS'
    a.assignedToId = u.id
    return HttpResponse.json(toAlertResponse(a))
  }),
  http.post(`${API}/alerts/:id/escalate`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const a = findAlert(params.id)
    if (!a) return err(404, 'Alerta não encontrado')
    const body = await jsonBody<{ justification: string }>(request)
    if (!validJustification(body.justification)) return err(422, JUSTIFICATION_MSG)
    if (['FALSE_POSITIVE', 'CLOSED'].includes(a.status)) return err(422, `Alerta em status ${a.status} não pode ser escalado`)
    if (a.status === 'ESCALATED') return err(422, 'Alerta já está escalado')
    const from = a.status
    a.status = 'ESCALATED'
    a.history = [...(a.history ?? []), { id: `h-${Date.now()}`, action: 'ESCALATE', fromStatus: from, toStatus: 'ESCALATED', actorId: u.id, actorName: u.name, justification: body.justification!.trim(), createdAt: new Date().toISOString() }]
    return HttpResponse.json(toAlertResponse(a))
  }),
  http.post(`${API}/alerts/:id/false-positive`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const a = findAlert(params.id)
    if (!a) return err(404, 'Alerta não encontrado')
    const body = await jsonBody<{ reason: string; justification: string }>(request)
    const fe = [
      ...(!body.reason?.trim() ? [{ field: 'reason', message: 'must not be blank' }] : []),
      ...(!body.justification?.trim() ? [{ field: 'justification', message: 'must not be blank' }] : []),
    ]
    if (fe.length) return invalid('Dados inválidos', fe)
    if (!validJustification(body.justification)) return err(422, JUSTIFICATION_MSG)
    a.status = 'FALSE_POSITIVE'
    a.falsePositiveReason = body.reason!
    return HttpResponse.json(toAlertResponse(a))
  }),

  /* ---------- cases ---------- */
  http.get(`${API}/cases`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const url = new URL(request.url)
    const p = url.searchParams
    const from = parseDay(p.get('from'), false)
    const to = parseDay(p.get('to'), true)
    if (from === null || to === null) return err(422, 'Período inválido: use AAAA-MM-DD ou ISO-8601')
    const status = multi(url, 'status')
    let items = db.cases.map(withSla)
    if (status.length) items = items.filter((c) => status.includes(c.status))
    if (p.get('assignedTo')) items = items.filter((c) => c.assignedToId === p.get('assignedTo'))
    if (p.get('slaStatus')) items = items.filter((c) => c.slaStatus === p.get('slaStatus'))
    if (p.get('riskCategory')) {
      const rc = p.get('riskCategory')
      items = items.filter((c) => (rc === 'PEP' ? !!partyOf(c.partyId)?.pep : c.partyRiskLevel === rc))
    }
    if (p.get('isEdd')) items = items.filter((c) => c.edd === (p.get('isEdd') === 'true'))
    if (p.get('partyId')) items = items.filter((c) => c.partyId === p.get('partyId'))
    if (from !== undefined) items = items.filter((c) => Date.parse(c.createdAt) >= from)
    if (to !== undefined) items = items.filter((c) => Date.parse(c.createdAt) <= to)
    return HttpResponse.json(paginate(items, url))
  }),
  http.post(`${API}/cases/from-alert/:alertId`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const body = await jsonBody<{ justification: string }>(request)
    return openCase(u, params.alertId, body.justification, false)
  }),
  http.post(`${API}/cases`, async ({ request }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const body = await jsonBody<{ alertId: string; justification: string }>(request)
    if (!body.alertId) return invalid('Dados inválidos', [{ field: 'alertId', message: 'must not be null' }])
    return openCase(u, body.alertId, body.justification, true)
  }),
  http.get(`${API}/cases/:id/dossier`, ({ request, params }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const c = findCase(params.id)
    if (!c) return err(404, 'Case não encontrado')
    const a = findAlert(c.alertId)
    const party = partyOf(c.partyId)
    const txs = txSummaries(c.partyId, 200)
    const dossier: CaseDossier = {
      caseInfo: withSla(c),
      alert: a ? { id: a.id, type: a.type, severity: a.severity, status: a.status, title: a.title, ruleCode: a.ruleCode, ruleVersion: a.ruleVersion, evidence: a.evidence } : null,
      party: partySummary(c.partyId),
      riskHistory: (pld.bundle.risk[c.partyId ?? ''] ?? []).slice(0, 20).map((r) => ({ previousLevel: r.previousLevel, newLevel: r.newLevel, previousScore: r.previousScore, newScore: r.newScore, reason: r.reason, changedByName: 'Sistema', createdAt: r.createdAt })),
      pep: party
        ? { pep: !!party.pep, status: party.pepStatus ?? null, category: party.pepCategory ?? null, role: party.pepRole ?? null, startDate: party.pepStartDate ?? null, endDate: party.pepEndDate ?? null, expiryDate: party.pepExpiryDate ?? null, approvedById: null, approvedByName: party.pepApprovalByName ?? null, approvedAt: party.pepApprovalAt ?? null }
        : null,
      ubos: (pld.bundle.ubo[c.partyId ?? ''] ?? []).map((o) => ({
        id: o.id, name: o.ownerName, document: o.ownerDocument, ownershipPct: o.ownershipPct, controlType: o.controlType, ownerType: o.ownerType ?? null, pep: !!o.pep, finalBeneficiary: o.finalBeneficiary, indirect: o.indirect,
        ownershipChain: (o.ownershipChain ?? []).map((l) => ({ name: l.name, pct: l.pct })),
      })),
      alertTransactions: txs.slice(0, 2),
      periodTransactions: txs,
      period: { from: new Date(Date.parse(c.occurredAt ?? c.createdAt) - 90 * D).toISOString(), to: c.selectedAt ?? c.createdAt },
      screeningHits: pld.hits
        .filter((h) => h.partyId === c.partyId)
        .map((h) => ({ id: h.id, listCode: h.listCode, listType: null, matchedName: h.matchedName, matchScore: h.matchScore, matchType: h.matchType, status: h.status, reviewJustification: h.reviewJustification, reviewedAt: h.reviewedAt, createdAt: h.createdAt })),
      evidences: db.evidences[c.id] ?? [],
      notes: db.notes[c.id] ?? [],
      auditTrail: db.caseTrail[c.id] ?? [],
    }
    return HttpResponse.json(dossier)
  }),
  http.get(`${API}/cases/:id/notes`, ({ request, params }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    return findCase(params.id) ? HttpResponse.json(db.notes[String(params.id)] ?? []) : err(404, 'Case não encontrado')
  }),
  http.post(`${API}/cases/:id/notes`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const c = findCase(params.id)
    if (!c) return err(404, 'Case não encontrado')
    const body = await jsonBody<{ text: string }>(request)
    if (!body.text?.trim()) return invalid('Dados inválidos', [{ field: 'text', message: 'must not be blank' }])
    if (CLOSED_CASE.includes(c.status)) return err(422, 'Case encerrado não recebe notas')
    const n: CaseNote = { id: `n-${Date.now()}`, authorId: u.id, authorName: u.name, text: body.text.trim(), createdAt: new Date().toISOString() }
    ;(db.notes[c.id] ??= []).push(n)
    trail(c.id, u, 'CASE_NOTE_ADDED', { noteId: n.id, length: n.text.length })
    return HttpResponse.json(n, { status: 201 })
  }),
  http.get(`${API}/cases/:id/evidences`, ({ request, params }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    return findCase(params.id) ? HttpResponse.json(db.evidences[String(params.id)] ?? []) : err(404, 'Case não encontrado')
  }),
  http.post(`${API}/cases/:id/evidences`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const c = findCase(params.id)
    if (!c) return err(404, 'Case não encontrado')
    const { file, fields } = await readMultipart(request)
    if (!file) return invalid('Dados inválidos', [{ field: 'file', message: 'required' }])
    if (CLOSED_CASE.includes(c.status)) return err(422, 'Case encerrado não recebe evidências')
    if (file.size > MAX_EVIDENCE_BYTES) return err(413, 'Arquivo excede o tamanho máximo permitido', 'PAYLOAD_TOO_LARGE')
    const ext = /\.(pdf|jpe?g|png)$/i.exec(file.name)?.[1]
    if (!ext) return err(422, 'Tipo de arquivo não permitido (use PDF, JPG ou PNG)')
    const desc = fields.description
    const e: CaseEvidence = {
      id: `e-${Date.now()}`, fileName: file.name.replace(/[^\w.\- ]/g, '_'), contentType: ext.toLowerCase() === 'pdf' ? 'application/pdf' : ext.toLowerCase() === 'png' ? 'image/png' : 'image/jpeg',
      sizeBytes: file.size, sha256: `${Date.now().toString(16)}`.padEnd(64, '0'), description: typeof desc === 'string' && desc.trim() ? desc.trim() : null,
      uploadedById: u.id, uploadedByName: u.name, uploadedAt: new Date().toISOString(),
    }
    ;(db.evidences[c.id] ??= []).push(e)
    trail(c.id, u, 'CASE_EVIDENCE_ADDED', { evidenceId: e.id })
    return HttpResponse.json(e, { status: 201 })
  }),
  http.get(`${API}/cases/:id/evidences/:eid/download`, ({ request, params }) => downloadEvidence(request, params)),
  http.get(`${API}/cases/:id/evidences/:eid`, ({ request, params }) => downloadEvidence(request, params)),
  http.get(`${API}/cases/:id`, ({ request, params }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const c = findCase(params.id)
    return c ? HttpResponse.json(withSla(c)) : err(404, 'Case não encontrado')
  }),
  http.post(`${API}/cases/:id/decide`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const c = findCase(params.id)
    if (!c) return err(404, 'Case não encontrado')
    const body = await jsonBody<{ decision: string; justification: string }>(request)
    if (body.decision !== 'COMMUNICATE' && body.decision !== 'ARCHIVE') return invalid('Dados inválidos', [{ field: 'decision', message: 'deve ser COMMUNICATE ou ARCHIVE' }])
    if (!validJustification(body.justification)) return err(422, JUSTIFICATION_MSG)
    if (c.decidedAt || ['ARCHIVED', 'COMMUNICATED', 'COMMUNICATE', 'PENDING_APPROVAL'].includes(c.status)) return err(422, 'Case já foi decidido. Não é possível alterar decisão final.')
    c.decision = body.decision
    c.decisionJustification = body.justification!.trim()
    c.decidedAt = new Date().toISOString()
    c.decidedById = u.id
    const party = partyOf(c.partyId)
    if (body.decision === 'COMMUNICATE' || c.edd || party?.pep || party?.riskLevel === 'HIGH' || party?.riskLevel === 'CRITICAL') {
      c.status = 'PENDING_APPROVAL'
    } else {
      c.status = 'ARCHIVED'
      c.closedAt = c.decidedAt
    }
    trail(c.id, u, 'CASE_DECIDED', { decision: c.decision })
    return HttpResponse.json(withSla(c))
  }),
  http.post(`${API}/cases/:id/escalate`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const c = findCase(params.id)
    if (!c) return err(404, 'Case não encontrado')
    const body = await jsonBody<{ justification: string }>(request)
    if (!validJustification(body.justification)) return err(422, JUSTIFICATION_MSG)
    c.status = 'ESCALATED'
    c.escalationLevel += 1
    trail(c.id, u, 'CASE_ESCALATED', { level: c.escalationLevel })
    return HttpResponse.json(withSla(c))
  }),
  http.post(`${API}/cases/:id/approve`, async ({ request, params }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const c = findCase(params.id)
    if (!c) return err(404, 'Case não encontrado')
    if (c.status !== 'PENDING_APPROVAL') return err(422, `Case nao esta aguardando aprovacao (status ${c.status})`)
    if (c.decidedById === u.id) return err(422, 'Quatro olhos: o aprovador deve ser diferente de quem decidiu o case')
    c.approvedById = u.id
    c.approvedAt = new Date().toISOString()
    if (c.decision === 'COMMUNICATE') {
      c.status = 'COMMUNICATE'
      c.communicationDeadline = new Date(Date.now() + D).toISOString()
      db2b.seq += 1
      db2b.siscoaf.unshift({
        id: `s-${db2b.seq}-case`, caseId: c.id, caseNumber: c.caseNumber, partyId: c.partyId, partyName: c.partyName, transactionId: null, originalCommunicationId: null, type: 'COS', status: 'DRAFT',
        protocolNumber: null, submittedAt: null, deadlineAt: c.communicationDeadline, decidedAt: c.decidedAt, referenceYear: new Date().getFullYear(), amount: c.totalAmountInvolved,
        indicatorCodes: c.indicatorCodes, periodFrom: null, periodTo: null, cancelledAt: null, cancelReason: null, createdById: u.id, approvedById: null, approvedAt: null,
        fourEyesRequired: true, payload: { caseNumber: c.caseNumber }, createdAt: new Date().toISOString(),
        statusHistory: [{ action: 'CREATED', status: 'DRAFT', fromStatus: null, byId: u.id, byName: u.name, reason: 'Rascunho gerado na aprovação do case', at: new Date().toISOString() }],
      })
    } else {
      c.status = 'ARCHIVED'
      c.closedAt = c.approvedAt
    }
    trail(c.id, u, 'CASE_APPROVED', { decision: c.decision })
    return HttpResponse.json(withSla(c))
  }),
  http.post(`${API}/cases/:id/reject-approval`, async ({ request, params }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const c = findCase(params.id)
    if (!c) return err(404, 'Case não encontrado')
    const body = await jsonBody<{ justification: string }>(request)
    if (!validJustification(body.justification)) return err(422, JUSTIFICATION_MSG)
    if (c.status !== 'PENDING_APPROVAL') return err(422, `Case nao esta aguardando aprovacao (status ${c.status})`)
    if (c.decidedById === u.id) return err(422, 'Quatro olhos: quem decidiu o case não pode rejeitar a própria aprovação')
    const rejected = { decision: c.decision, justification: body.justification!.trim() }
    const decisor = c.decidedById
    c.decision = null
    c.decisionJustification = null
    c.decidedAt = null
    c.decidedById = null
    c.status = 'IN_ANALYSIS'
    trail(c.id, u, 'CASE_APPROVAL_REJECTED', rejected)
    if (decisor) {
      ;(db2b.notifications[decisor] ??= []).unshift({
        id: `n-${Date.now()}`, type: 'CASE_APPROVAL_REJECTED', title: 'Aprovação rejeitada', message: `${c.caseNumber} voltou para análise.`, read: false, entityType: 'CASE', entityId: c.id, createdAt: new Date().toISOString(),
      })
    }
    return HttpResponse.json(withSla(c))
  }),
]

function downloadEvidence(request: Request, params: Record<string, unknown>) {
  const u = guard(request)
  if (isResponse(u)) return u
  const e = (db.evidences[String(params.id)] ?? []).find((x) => x.id === params.eid)
  if (!e) return err(404, 'Evidência não encontrada')
  return new HttpResponse('conteudo-mock', { headers: { 'Content-Type': e.contentType, 'X-Content-Type-Options': 'nosniff' } })
}
