import { http, HttpResponse } from 'msw'
import { AUDIT, API, err, guard, isResponse, paginate, parseDay } from './helpers'
import { db2b } from './db2b'

/** Filtros server-side de /audit e /data-access: userId|username, action, entityType, entityId, from, to (padrão createdAt,desc). */
function filterBy<T extends { user: { id: string; username: string } | null; entityType: string | null; entityId: string | null; createdAt: string; action?: string }>(items: T[], url: URL) {
  const p = url.searchParams
  const from = parseDay(p.get('from'), false)
  const to = parseDay(p.get('to'), true)
  if (from === null || to === null) return null
  let out = items
  if (p.get('userId')) out = out.filter((e) => e.user?.id === p.get('userId'))
  if (p.get('username')) out = out.filter((e) => e.user?.username === p.get('username'))
  if (p.get('action')) out = out.filter((e) => e.action === p.get('action'))
  if (p.get('entityType')) out = out.filter((e) => e.entityType === p.get('entityType'))
  if (p.get('entityId')) out = out.filter((e) => e.entityId === p.get('entityId'))
  if (from !== undefined) out = out.filter((e) => Date.parse(e.createdAt) >= from)
  if (to !== undefined) out = out.filter((e) => Date.parse(e.createdAt) <= to)
  return out
}

const withDefaultSort = (url: URL) => {
  if (!url.searchParams.get('sort')) url.searchParams.set('sort', 'createdAt,desc')
  return url
}

export const auditHandlers = [
  http.get(`${API}/audit/entity/:type/:id`, ({ request, params }) => {
    const u = guard(request, AUDIT)
    if (isResponse(u)) return u
    const own = db2b.audit.filter((e) => e.entityType === params.type && e.entityId === params.id)
    return HttpResponse.json(paginate(own, withDefaultSort(new URL(request.url))))
  }),
  http.get(`${API}/audit`, ({ request }) => {
    const u = guard(request, AUDIT)
    if (isResponse(u)) return u
    const url = withDefaultSort(new URL(request.url))
    const items = filterBy(db2b.audit, url)
    return items ? HttpResponse.json(paginate(items, url)) : err(400, 'Período inválido: use AAAA-MM-DD ou ISO-8601')
  }),
  http.get(`${API}/data-access/entity/:type/:id`, ({ request, params }) => {
    const u = guard(request, AUDIT)
    if (isResponse(u)) return u
    return HttpResponse.json(paginate(db2b.access.filter((e) => e.entityType === params.type && e.entityId === params.id), withDefaultSort(new URL(request.url))))
  }),
  http.get(`${API}/data-access/user/:userId`, ({ request, params }) => {
    const u = guard(request, AUDIT)
    if (isResponse(u)) return u
    return HttpResponse.json(paginate(db2b.access.filter((e) => e.user?.id === params.userId), withDefaultSort(new URL(request.url))))
  }),
  http.get(`${API}/data-access`, ({ request }) => {
    const u = guard(request, AUDIT)
    if (isResponse(u)) return u
    const url = withDefaultSort(new URL(request.url))
    const items = filterBy(db2b.access, url)
    return items ? HttpResponse.json(paginate(items, url)) : err(400, 'Período inválido: use AAAA-MM-DD ou ISO-8601')
  }),
]
