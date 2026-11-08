import { http, HttpResponse } from 'msw'
import { db } from '../data'
import { API, guard, isResponse, paginate } from './helpers'
import { db2b } from './db2b'

const AUDIT = ['AUDITOR', 'ADMIN', 'SUPERVISOR']

/**
 * ATENÇÃO: este handler precisa vir ANTES do `GET /audit/entity` de handlers.ts (MSW usa o primeiro que casa).
 * Para CASE sem registros próprios mantém os eventos sintéticos do dossiê da etapa 1.
 */
export const auditHandlers = [
  http.get(`${API}/audit/entity/:type/:id`, ({ request, params }) => {
    const u = guard(request, AUDIT)
    if (isResponse(u)) return u
    const own = db2b.audit.filter((e) => e.entityType === params.type && e.entityId === params.id)
    if (own.length > 0 || params.type !== 'CASE') return HttpResponse.json(paginate(own, new URL(request.url)))
    const c = db.cases.find((x) => x.id === params.id)
    const events = [
      { id: 'au-1', action: 'CASE_CREATED', entityType: 'CASE', entityId: String(params.id), user: { id: 'u-analyst', name: 'Ana Martins' }, createdAt: c?.createdAt ?? new Date().toISOString() },
      ...(c?.decidedAt ? [{ id: 'au-2', action: 'CASE_DECIDED', entityType: 'CASE', entityId: String(params.id), user: { id: 'u-analyst', name: 'Ana Martins' }, createdAt: c.decidedAt }] : []),
    ]
    return HttpResponse.json(paginate(events, new URL(request.url)))
  }),
  http.get(`${API}/data-access/entity/:type/:id`, ({ request, params }) => {
    const u = guard(request, AUDIT)
    if (isResponse(u)) return u
    return HttpResponse.json(paginate(db2b.access.filter((e) => e.entityType === params.type && e.entityId === params.id), new URL(request.url)))
  }),
  http.get(`${API}/data-access/user/:userId`, ({ request, params }) => {
    const u = guard(request, AUDIT)
    if (isResponse(u)) return u
    return HttpResponse.json(paginate(db2b.access.filter((e) => e.user?.id === params.userId), new URL(request.url)))
  }),
]
