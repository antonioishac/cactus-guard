import { http, HttpResponse } from 'msw'
import type { Alert, AuthResponse, Case, Page } from '@/api/types'
import { MOCK_PASSWORD, MOCK_USERS, REVEALED, db } from './data'
import type { MockUser } from './data'
import { handlers2B } from './handlers/all2b'
import { pldHandlers } from './handlers/pld'

const API = '*/api/v1'

/* ---------- helpers ---------- */

function err(status: number, message: string) {
  return HttpResponse.json(
    { timestamp: new Date().toISOString(), status, error: String(status), message, traceId: 'mock-trace-0001' },
    { status },
  )
}

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

function userFrom(request: Request): MockUser | null {
  const m = /^Bearer acc\.([^.]+)\./.exec(request.headers.get('authorization') ?? '')
  return MOCK_USERS.find((u) => u.id === m?.[1]) ?? null
}

type Ctx = { request: Request }
/** Exige login e (opcionalmente) role; devolve usuário ou a resposta de erro. */
function guard(request: Request, roles?: string[]): MockUser | Response {
  const u = userFrom(request)
  if (!u) return err(401, 'Não autenticado')
  if (roles && !u.roles.some((r) => roles.includes(r))) return err(403, 'Acesso negado')
  return u
}
const isResponse = (v: MockUser | Response): v is Response => v instanceof Response

const ANALYSTS = ['ANALYST', 'SUPERVISOR']

function paginate<T>(items: T[], url: URL): Page<T> {
  const page = Number(url.searchParams.get('page') ?? 0)
  const size = Number(url.searchParams.get('size') ?? 20)
  const [field, dir] = (url.searchParams.get('sort') ?? '').split(',')
  const sorted = [...items]
  if (field) {
    sorted.sort((a, b) => {
      const av = (a as Record<string, unknown>)[field]
      const bv = (b as Record<string, unknown>)[field]
      if (av == null && bv == null) return 0
      if (av == null) return 1
      if (bv == null) return -1
      return (av < bv ? -1 : av > bv ? 1 : 0) * (dir === 'desc' ? -1 : 1)
    })
  }
  return { content: sorted.slice(page * size, page * size + size), totalElements: items.length, totalPages: Math.ceil(items.length / size), number: page, size }
}

const MIN_JUSTIFICATION = 20
const validJustification = (s: unknown) => typeof s === 'string' && s.replace(/\s/g, '').length >= MIN_JUSTIFICATION

function findCase(id: string | readonly string[] | undefined): Case | undefined {
  return db.cases.find((c) => c.id === id)
}
function findAlert(id: string | readonly string[] | undefined) {
  return db.alerts.find((a) => a.id === id)
}

/* ---------- handlers ---------- */

export const handlers = [
  // 2B primeiro: o GET /audit/entity daqui sobrepõe o da etapa 1 (mantendo os eventos sintéticos de CASE)
  ...handlers2B,
  // etapa 2A (partes/KYC, screening, watchlist, usuários): precede os handlers antigos de /parties/*
  ...pldHandlers,
  http.post(`${API}/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { username: string; password: string }
    const u = MOCK_USERS.find((x) => x.username === body.username)
    if (!u || body.password !== MOCK_PASSWORD) return err(401, 'Credenciais inválidas')
    return HttpResponse.json(issue(u))
  }),
  http.post(`${API}/auth/refresh`, async ({ request }) => {
    const body = (await request.json()) as { refreshToken: string }
    const id = /^ref\.([^.]+)\./.exec(body.refreshToken)?.[1]
    const u = MOCK_USERS.find((x) => x.id === id)
    return u ? HttpResponse.json(issue(u)) : err(401, 'Refresh token inválido')
  }),
  http.post(`${API}/auth/logout`, () => new HttpResponse(null, { status: 204 })),

  /* dashboard */
  http.get(`${API}/dashboard/operational`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const open = db.alerts.filter((a) => a.status === 'OPEN')
    const openCases = db.cases.filter((c) => c.status === 'OPEN').length
    return HttpResponse.json({
      openAlerts: open.length, openCases, pendingKyc: 12,
      criticalAlerts: open.filter((a) => a.severity === 'CRITICAL').length,
      alertCaseRatio: open.length ? openCases / open.length : 0,
    })
  }),
  http.get(`${API}/dashboard/metrics`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const byStatus: Record<string, number> = {}
    db.alerts.forEach((a) => (byStatus[a.status] = (byStatus[a.status] ?? 0) + 1))
    return HttpResponse.json({ alertsByStatus: byStatus, screenings: 1284, activePeps: 8 })
  }),
  http.get(`${API}/dashboard/alerts-chart`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    return HttpResponse.json(Array.from({ length: 7 }, (_, i) => ({ date: new Date(Date.now() - (6 - i) * 86_400_000).toISOString().slice(0, 10), total: 4 + ((i * 5) % 9) })))
  }),
  http.get(`${API}/dashboard/severity-distribution`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    return HttpResponse.json((['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const).map((s) => ({ severity: s, count: db.alerts.filter((a) => a.severity === s).length })))
  }),
  http.get(`${API}/dashboard/case-sla`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    return HttpResponse.json({
      selection: { onTime: 14, dueSoon: 5, overdue: 2 }, analysis: { onTime: 9, dueSoon: 4, overdue: 1 },
      communication: { onTime: 2, dueSoon: 1, overdue: 0 }, pendingApproval: db.cases.filter((c) => c.status === 'PENDING_APPROVAL').length,
    })
  }),

  /* alertas */
  http.get(`${API}/alerts`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const url = new URL(request.url)
    const p = url.searchParams
    let items: Alert[] = db.alerts
    if (p.get('status')) items = items.filter((a) => a.status === p.get('status'))
    if (p.get('severity')) items = items.filter((a) => a.severity === p.get('severity'))
    if (p.get('type')) items = items.filter((a) => a.type === p.get('type'))
    if (p.get('assignedTo')) items = items.filter((a) => a.assignedToId === p.get('assignedTo'))
    if (p.get('partyId')) items = items.filter((a) => a.partyId === p.get('partyId'))
    return HttpResponse.json(paginate(items, url))
  }),
  http.get(`${API}/alerts/:id`, ({ request, params }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const a = findAlert(params.id)
    return a ? HttpResponse.json(a) : err(404, 'Alerta não encontrado')
  }),
  http.post(`${API}/alerts/:id/assume`, ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const a = findAlert(params.id)
    if (!a) return err(404, 'Alerta não encontrado')
    a.status = 'IN_ANALYSIS'
    a.assignedToId = u.id
    return HttpResponse.json(a)
  }),
  http.post(`${API}/alerts/:id/escalate`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const a = findAlert(params.id)
    if (!a) return err(404, 'Alerta não encontrado')
    const body = (await request.json()) as { justification?: string }
    if (!body.justification?.trim()) return err(422, 'Justificativa obrigatória')
    a.status = 'ESCALATED'
    return HttpResponse.json(a)
  }),
  http.post(`${API}/alerts/:id/false-positive`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const a = findAlert(params.id)
    if (!a) return err(404, 'Alerta não encontrado')
    const body = (await request.json()) as { reason?: string; justification?: string }
    if (!body.reason?.trim() || !body.justification?.trim()) return err(400, 'reason/justification: obrigatórios')
    a.status = 'FALSE_POSITIVE'
    a.falsePositiveReason = body.reason
    return HttpResponse.json(a)
  }),

  /* cases */
  http.get(`${API}/cases`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const url = new URL(request.url)
    let items = db.cases
    if (url.searchParams.get('status')) items = items.filter((c) => c.status === url.searchParams.get('status'))
    if (url.searchParams.get('partyId')) items = items.filter((c) => c.partyId === url.searchParams.get('partyId'))
    return HttpResponse.json(paginate(items, url))
  }),
  http.get(`${API}/cases/:id`, ({ request, params }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const c = findCase(params.id)
    return c ? HttpResponse.json(c) : err(404, 'Case não encontrado')
  }),
  http.post(`${API}/cases/from-alert/:alertId`, ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const a = findAlert(params.alertId)
    if (!a) return err(404, 'Alerta não encontrado')
    const now = Date.now()
    const c: Case = {
      id: `c-${String(db.cases.length + 1).padStart(4, '0')}`, alertId: a.id, partyId: a.partyId, partyName: a.partyName, status: 'OPEN', priority: a.severity,
      assignedToId: u.id, decision: null, decisionJustification: null, decidedAt: null, slaDueDate: null, occurredAt: a.occurredAt,
      selectedAt: new Date(now).toISOString(), analysisDeadline: new Date(now + 45 * 86_400_000).toISOString(), communicationDeadline: null,
      escalationLevel: 0, edd: false, approvedById: null, approvedAt: null, closedAt: null, createdAt: new Date(now).toISOString(), decidedById: null,
    }
    db.cases.unshift(c)
    return HttpResponse.json(c, { status: 201 })
  }),
  http.post(`${API}/cases/:id/decide`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const c = findCase(params.id)
    if (!c) return err(404, 'Case não encontrado')
    const body = (await request.json()) as { decision?: string; justification?: string }
    if (body.decision !== 'COMMUNICATE' && body.decision !== 'ARCHIVE') return err(422, 'Decisão inválida')
    if (!validJustification(body.justification)) return err(422, `Justificativa obrigatória (mínimo de ${MIN_JUSTIFICATION} caracteres)`)
    if (c.decidedAt || ['ARCHIVED', 'CLOSED', 'COMMUNICATE', 'PENDING_APPROVAL'].includes(c.status)) return err(422, 'Case já foi decidido. Não é possível alterar decisão final.')
    c.decision = body.decision
    c.decisionJustification = body.justification!.trim()
    c.decidedAt = new Date().toISOString()
    c.decidedById = u.id
    const party = db.parties.find((p) => p.id === c.partyId)
    if (body.decision === 'COMMUNICATE' || c.edd || party?.pep) {
      c.status = 'PENDING_APPROVAL'
    } else {
      c.status = 'ARCHIVED'
      c.closedAt = c.decidedAt
    }
    return HttpResponse.json(c)
  }),
  http.post(`${API}/cases/:id/escalate`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const c = findCase(params.id)
    if (!c) return err(404, 'Case não encontrado')
    const body = (await request.json()) as { justification?: string }
    if (!validJustification(body.justification)) return err(422, `Justificativa obrigatória (mínimo de ${MIN_JUSTIFICATION} caracteres)`)
    c.status = 'ESCALATED'
    c.escalationLevel += 1
    return HttpResponse.json(c)
  }),
  http.post(`${API}/cases/:id/approve`, async ({ request, params }) => {
    const u = guard(request, ['SUPERVISOR'])
    if (isResponse(u)) return u
    const c = findCase(params.id)
    if (!c) return err(404, 'Case não encontrado')
    if (c.status !== 'PENDING_APPROVAL') return err(422, `Case nao esta aguardando aprovacao (status ${c.status})`)
    if (c.decidedById === u.id) return err(422, 'Quatro olhos: o aprovador deve ser diferente de quem decidiu o case')
    c.approvedById = u.id
    c.approvedAt = new Date().toISOString()
    if (c.decision === 'COMMUNICATE') {
      c.status = 'COMMUNICATE'
      c.communicationDeadline = new Date(Date.now() + 86_400_000).toISOString()
    } else {
      c.status = 'ARCHIVED'
      c.closedAt = c.approvedAt
    }
    return HttpResponse.json(c)
  }),
  http.get(`${API}/cases/:id/notes`, ({ request, params }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    return HttpResponse.json(db.notes[String(params.id)] ?? [])
  }),
  http.post(`${API}/cases/:id/notes`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const body = (await request.json()) as { text: string }
    const n = { id: `n-${Date.now()}`, authorName: u.name, text: body.text, createdAt: new Date().toISOString() }
    const id = String(params.id)
    db.notes[id] = [...(db.notes[id] ?? []), n]
    return HttpResponse.json(n, { status: 201 })
  }),
  http.get(`${API}/cases/:id/evidences`, ({ request, params }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    return HttpResponse.json(db.evidences[String(params.id)] ?? [])
  }),
  http.post(`${API}/cases/:id/evidences`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const form = await request.formData()
    const file = form.get('file') as File | null
    const e = { id: `e-${Date.now()}`, fileName: file?.name ?? 'arquivo', sizeBytes: file?.size ?? 0, sha256: '0'.repeat(64), uploadedByName: u.name, uploadedAt: new Date().toISOString() }
    const id = String(params.id)
    db.evidences[id] = [...(db.evidences[id] ?? []), e]
    return HttpResponse.json(e, { status: 201 })
  }),
  http.get(`${API}/cases/:id/evidences/:eid/download`, ({ request }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    return new HttpResponse('conteudo-mock', { headers: { 'Content-Type': 'application/octet-stream' } })
  }),

  /* parte e dados do dossiê */
  http.get(`${API}/parties/:id`, ({ request, params }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const p = db.parties.find((x) => x.id === params.id)
    return p ? HttpResponse.json(p) : err(404, 'Parte não encontrada')
  }),
  http.post(`${API}/parties/:id/reveal`, async ({ request, params }: Ctx & { params: Record<string, unknown> }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const body = (await request.json()) as { field: string; reason: string }
    db.revealLog.push({ partyId: String(params.id), field: body.field, reason: body.reason, by: u.id })
    return HttpResponse.json({ field: body.field, value: REVEALED[body.field] ?? '' })
  }),
  http.get(`${API}/parties/:id/beneficial-owners`, ({ request }) => {
    const u = guard(request)
    return isResponse(u) ? u : HttpResponse.json(db.ubo)
  }),
  http.get(`${API}/risk-history/party/:id`, ({ request }) => {
    const u = guard(request)
    return isResponse(u) ? u : HttpResponse.json(paginate(db.risk, new URL(request.url)))
  }),
  http.get(`${API}/screening/party/:id`, ({ request }) => {
    const u = guard(request)
    return isResponse(u) ? u : HttpResponse.json(db.screening)
  }),
  http.get(`${API}/transactions/party/:id`, ({ request, params }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    return HttpResponse.json(paginate(db.transactions.filter((t) => t.partyId === params.id), new URL(request.url)))
  }),
  http.get(`${API}/transactions/:id`, ({ request, params }) => {
    const u = guard(request)
    if (isResponse(u)) return u
    const t = db.transactions.find((x) => x.id === params.id)
    return t ? HttpResponse.json(t) : err(404, 'Transação não encontrada')
  }),
  http.get(`${API}/audit/entity/:type/:id`, ({ request, params }) => {
    const u = guard(request, ['AUDITOR', 'ADMIN', 'SUPERVISOR'])
    if (isResponse(u)) return u
    const c = findCase(params.id)
    const events = [
      { id: 'au-1', action: 'CASE_CREATED', entityType: 'CASE', entityId: String(params.id), user: { id: 'u-analyst', name: 'Ana Martins' }, createdAt: c?.createdAt ?? new Date().toISOString() },
      ...(c?.decidedAt ? [{ id: 'au-2', action: 'CASE_DECIDED', entityType: 'CASE', entityId: String(params.id), user: { id: 'u-analyst', name: 'Ana Martins' }, createdAt: c.decidedAt }] : []),
    ]
    return HttpResponse.json(paginate(events, new URL(request.url)))
  }),
]
