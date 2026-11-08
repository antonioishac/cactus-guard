import { http, HttpResponse } from 'msw'
import type { SiscoafCommunication, SiscoafHistoryEntry } from '@/api/types'
import type { MockUser } from '../data'
import { API, JUSTIFICATION_MSG, READ, SUP, err, guard, isResponse, jsonBody, multi, paginate, parseDay, validJustification } from './helpers'
import { db2b } from './db2b'

const CANCELLABLE = ['DRAFT', 'PENDING_REVIEW', 'APPROVED']
const SENT = ['SUBMITTED', 'ACKNOWLEDGED']

const find = (id: unknown) => db2b.siscoaf.find((c) => c.id === id)

function push(c: SiscoafCommunication, u: MockUser, action: string, status: string, fromStatus: string | null, reason: string | null) {
  const h: SiscoafHistoryEntry = { action, status, fromStatus, byId: u.id, byName: u.name, reason, at: new Date().toISOString() }
  c.statusHistory = [...(c.statusHistory ?? []), h]
}

/** Lista não traz `statusHistory` (só o detalhe). */
function toListItem(c: SiscoafCommunication): SiscoafCommunication {
  const copy = { ...c }
  delete copy.statusHistory
  return copy
}

export const siscoafHandlers = [
  http.get(`${API}/siscoaf`, ({ request }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    const url = new URL(request.url)
    const p = url.searchParams
    const from = parseDay(p.get('from'), false)
    const to = parseDay(p.get('to'), true)
    if (from === null || to === null) return err(422, 'Período inválido: use AAAA-MM-DD ou ISO-8601')
    const status = multi(url, 'status')
    const type = multi(url, 'type')
    let items = db2b.siscoaf
    // sem `status` lista todos (não assume DRAFT)
    if (status.length) items = items.filter((c) => status.includes(c.status))
    if (type.length) items = items.filter((c) => type.includes(c.type))
    if (p.get('partyId')) items = items.filter((c) => c.partyId === p.get('partyId'))
    if (p.get('caseId')) items = items.filter((c) => c.caseId === p.get('caseId'))
    if (from !== undefined) items = items.filter((c) => Date.parse(c.createdAt) >= from)
    if (to !== undefined) items = items.filter((c) => Date.parse(c.createdAt) <= to)
    return HttpResponse.json(paginate(items.map(toListItem), url))
  }),
  http.get(`${API}/siscoaf/:id`, ({ request, params }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    const c = find(params.id)
    return c ? HttpResponse.json(c) : err(404, 'Comunicação não encontrada')
  }),
  http.post(`${API}/siscoaf/negative-declaration/:year`, ({ request, params }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const year = Number(params.year)
    if (db2b.siscoaf.some((c) => c.type === 'NEGATIVE_DECLARATION' && c.referenceYear === year && !['CANCELLED', 'RECTIFIED', 'REJECTED'].includes(c.status))) {
      return err(422, `Ja existe declaracao negativa vigente para ${year}`)
    }
    const sent = db2b.siscoaf.filter((c) => c.type !== 'NEGATIVE_DECLARATION' && SENT.includes(c.status) && c.submittedAt?.startsWith(String(year))).length
    if (sent > 0) return err(422, `Declaracao negativa nao permitida: ha ${sent} comunicacao(oes) COS/COE enviada(s) em ${year}`)
    db2b.seq += 1
    const now = new Date().toISOString()
    const created: SiscoafCommunication = {
      id: `s-${db2b.seq}-neg`, caseId: null, caseNumber: null, partyId: null, partyName: null, transactionId: null, originalCommunicationId: null, type: 'NEGATIVE_DECLARATION', status: 'DRAFT',
      protocolNumber: null, submittedAt: null, deadlineAt: null, decidedAt: null, referenceYear: year, amount: null, indicatorCodes: null, periodFrom: null, periodTo: null,
      cancelledAt: null, cancelReason: null, createdById: u.id, approvedById: null, approvedAt: null, fourEyesRequired: false, payload: { referenceYear: year }, createdAt: now,
      statusHistory: [{ action: 'CREATED', status: 'DRAFT', fromStatus: null, byId: u.id, byName: u.name, reason: null, at: now }],
    }
    db2b.siscoaf.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),
  http.post(`${API}/siscoaf/:id/approve`, async ({ request, params }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const c = find(params.id)
    if (!c) return err(404, 'Comunicação não encontrada')
    const body = await jsonBody<{ comment: string; justification: string }>(request)
    const reason = body.comment ?? body.justification
    if (!validJustification(reason)) return err(422, JUSTIFICATION_MSG)
    if (!['DRAFT', 'PENDING_REVIEW'].includes(c.status)) return err(422, `Cannot approve in status: ${c.status}`)
    if (c.fourEyesRequired && c.createdById === u.id) return err(422, 'Quatro olhos: o aprovador deve ser diferente de quem criou o rascunho')
    const from = c.status
    c.status = 'APPROVED'
    c.approvedById = u.id
    c.approvedAt = new Date().toISOString()
    push(c, u, 'APPROVED', 'APPROVED', from, reason!.trim())
    return HttpResponse.json(c)
  }),
  http.post(`${API}/siscoaf/:id/submit`, async ({ request, params }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const c = find(params.id)
    if (!c) return err(404, 'Comunicação não encontrada')
    const body = await jsonBody<{ reason: string }>(request)
    if (body.reason !== undefined && !validJustification(body.reason)) return err(422, JUSTIFICATION_MSG)
    if (!['DRAFT', 'APPROVED'].includes(c.status)) return err(422, `Cannot submit in status: ${c.status}`)
    if (c.status === 'DRAFT' && c.fourEyesRequired) return err(422, 'Comunicação com quatro olhos exigido precisa ser aprovada antes do envio')
    const from = c.status
    c.status = 'SUBMITTED'
    c.submittedAt = new Date().toISOString()
    db2b.seq += 1
    c.protocolNumber = `COAF-${new Date().getFullYear()}-${String(900 + db2b.seq)}`
    push(c, u, 'SUBMITTED', 'SUBMITTED', from, body.reason?.trim() ?? null)
    return HttpResponse.json(c)
  }),
  http.post(`${API}/siscoaf/:id/cancel`, async ({ request, params }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const c = find(params.id)
    if (!c) return err(404, 'Comunicação não encontrada')
    const body = await jsonBody<{ reason: string }>(request)
    if (!validJustification(body.reason)) return err(422, 'Justificativa obrigatória (mínimo de 20 caracteres) para cancelar comunicacao')
    if (!CANCELLABLE.includes(c.status)) return err(422, `Comunicacao em status ${c.status} nao pode ser cancelada (use retificacao)`)
    const from = c.status
    c.status = 'CANCELLED'
    c.cancelledAt = new Date().toISOString()
    c.cancelReason = body.reason ?? null
    push(c, u, 'CANCELLED', 'CANCELLED', from, c.cancelReason)
    return HttpResponse.json(c)
  }),
  http.post(`${API}/siscoaf/:id/rectify`, async ({ request, params }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const c = find(params.id)
    if (!c) return err(404, 'Comunicação não encontrada')
    const body = await jsonBody<{ reason: string }>(request)
    if (!validJustification(body.reason)) return err(422, 'Justificativa obrigatória (mínimo de 20 caracteres) para retificar comunicacao')
    if (!SENT.includes(c.status)) return err(422, 'So comunicacoes enviadas podem ser retificadas')
    const from = c.status
    c.status = 'RECTIFIED'
    push(c, u, 'RECTIFIED', 'RECTIFIED', from, body.reason!.trim())
    db2b.seq += 1
    const now = new Date().toISOString()
    const copy: SiscoafCommunication = {
      ...structuredClone(c), id: `s-${db2b.seq}-rect`, status: 'DRAFT', originalCommunicationId: c.id, protocolNumber: null, submittedAt: null, approvedById: null, approvedAt: null, createdAt: now,
      statusHistory: [{ action: 'CREATED', status: 'DRAFT', fromStatus: null, byId: u.id, byName: u.name, reason: 'Retificação', at: now }],
    }
    db2b.siscoaf.push(copy)
    return HttpResponse.json(copy, { status: 201 })
  }),
]
