import { http, HttpResponse } from 'msw'
import { API, READ, err, guard, isResponse, paginate } from './helpers'
import { db2b } from './db2b'

export const notificationsHandlers = [
  http.get(`${API}/notifications/unread-count`, ({ request }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    return HttpResponse.json({ count: (db2b.notifications[u.id] ?? []).filter((n) => !n.read).length })
  }),
  http.get(`${API}/notifications`, ({ request }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    const url = new URL(request.url)
    const unreadOnly = url.searchParams.get('unreadOnly') === 'true'
    return HttpResponse.json(paginate((db2b.notifications[u.id] ?? []).filter((n) => !unreadOnly || !n.read), url))
  }),
  // rota estática antes de /:id/read
  http.post(`${API}/notifications/read-all`, ({ request }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    const mine = (db2b.notifications[u.id] ??= []).filter((n) => !n.read)
    mine.forEach((n) => (n.read = true))
    return HttpResponse.json({ updated: mine.length })
  }),
  http.post(`${API}/notifications/:id/read`, ({ request, params }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    // só notificação do próprio usuário
    const n = (db2b.notifications[u.id] ?? []).find((x) => x.id === params.id)
    if (!n) return err(404, 'Notificação não encontrada')
    n.read = true
    return new HttpResponse(null, { status: 204 })
  }),
]
