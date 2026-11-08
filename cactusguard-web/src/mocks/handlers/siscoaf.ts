import { http, HttpResponse } from 'msw'
import type { SiscoafCommunication } from '@/api/types'
import { API, err, guard, isResponse, paginate, validJustification } from './helpers'
import { db2b } from './db2b'

const READ = ['ANALYST', 'SUPERVISOR', 'ADMIN', 'AUDITOR']
const SUP = ['SUPERVISOR']
const CANCELLABLE = ['DRAFT', 'PENDING_REVIEW', 'APPROVED']
const SENT = ['SUBMITTED', 'ACKNOWLEDGED']
let seq = 100

const find = (id: unknown) => db2b.siscoaf.find((c) => c.id === id)

export const siscoafHandlers = [
  http.get(`${API}/siscoaf`, ({ request }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    const url = new URL(request.url)
    // como o backend: um status por vez (default DRAFT); `type` é ignorado
    const status = url.searchParams.get('status') ?? 'DRAFT'
    return HttpResponse.json(paginate(db2b.siscoaf.filter((c) => c.status === status), url))
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
    const created: SiscoafCommunication = {
      id: `s-${++seq}-neg`, caseId: null, partyId: null, transactionId: null, originalCommunicationId: null, type: 'NEGATIVE_DECLARATION', status: 'DRAFT',
      protocolNumber: null, submittedAt: null, deadlineAt: null, decidedAt: null, referenceYear: year, amount: null, cancelledAt: null,
      payload: { referenceYear: year }, createdAt: new Date().toISOString(),
    }
    db2b.siscoaf.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),
  http.post(`${API}/siscoaf/:id/approve`, ({ request, params }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const c = find(params.id)
    if (!c) return err(404, 'Comunicação não encontrada')
    if (!['DRAFT', 'PENDING_REVIEW'].includes(c.status)) return err(422, `Cannot approve in status: ${c.status}`)
    c.status = 'APPROVED'
    return HttpResponse.json(c)
  }),
  http.post(`${API}/siscoaf/:id/submit`, ({ request, params }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const c = find(params.id)
    if (!c) return err(404, 'Comunicação não encontrada')
    if (!['DRAFT', 'APPROVED'].includes(c.status)) return err(422, `Cannot submit in status: ${c.status}`)
    c.status = 'SUBMITTED'
    c.submittedAt = new Date().toISOString()
    c.protocolNumber = `COAF-${new Date().getFullYear()}-${String(900 + ++seq)}`
    return HttpResponse.json(c)
  }),
  http.post(`${API}/siscoaf/:id/cancel`, async ({ request, params }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const c = find(params.id)
    if (!c) return err(404, 'Comunicação não encontrada')
    const body = (await request.json().catch(() => ({}))) as { reason?: string }
    if (!validJustification(body.reason)) return err(422, 'Justificativa obrigatória (mínimo de 20 caracteres) para cancelar comunicacao')
    if (!CANCELLABLE.includes(c.status)) return err(422, `Comunicacao em status ${c.status} nao pode ser cancelada (use retificacao)`)
    c.status = 'CANCELLED'
    c.cancelledAt = new Date().toISOString()
    c.cancelReason = body.reason ?? null
    return HttpResponse.json(c)
  }),
  http.post(`${API}/siscoaf/:id/rectify`, async ({ request, params }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const c = find(params.id)
    if (!c) return err(404, 'Comunicação não encontrada')
    const body = (await request.json().catch(() => ({}))) as { reason?: string }
    if (!validJustification(body.reason)) return err(422, 'Justificativa obrigatória (mínimo de 20 caracteres) para retificar comunicacao')
    if (!SENT.includes(c.status)) return err(422, 'So comunicacoes enviadas podem ser retificadas')
    c.status = 'RECTIFIED'
    const copy: SiscoafCommunication = { ...structuredClone(c), id: `s-${++seq}-rect`, status: 'DRAFT', originalCommunicationId: c.id, protocolNumber: null, submittedAt: null, createdAt: new Date().toISOString() }
    db2b.siscoaf.push(copy)
    return HttpResponse.json(copy, { status: 201 })
  }),
]
