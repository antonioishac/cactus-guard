/** Handlers MSW da etapa 2A: partes/KYC/PEP/risco, screening, watchlist, KYC e usuários. */
import { http, HttpResponse } from 'msw'
import type { KycProcess, Page, Party, PartyRequest, PepRequest, ScreeningResult, Transaction, UserAccount } from '@/api/types'
import { MOCK_USERS } from '../data'
import type { MockUser } from '../data'
import { pld } from '../pldData'

const API = '*/api/v1'
const ANALYSTS = ['ANALYST', 'SUPERVISOR']
const READERS = ['ANALYST', 'SUPERVISOR', 'ADMIN', 'AUDITOR']

function err(status: number, message: string) {
  return HttpResponse.json({ timestamp: new Date().toISOString(), status, error: String(status), message }, { status })
}
function userFrom(request: Request): MockUser | null {
  const m = /^Bearer acc\.([^.]+)\./.exec(request.headers.get('authorization') ?? '')
  return MOCK_USERS.find((u) => u.id === m?.[1]) ?? null
}
function guard(request: Request, roles: string[]): MockUser | Response {
  const u = userFrom(request)
  if (!u) return err(401, 'Não autenticado')
  if (!u.roles.some((r) => roles.includes(r))) return err(403, 'Acesso negado')
  return u
}
const isResponse = (v: MockUser | Response): v is Response => v instanceof Response

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

const strip = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
const onlyDigits = (s: string) => s.replace(/\D/g, '')
const nonBlank = (s: string | null | undefined) => (s ?? '').replace(/\s/g, '').length
const maskDoc = (d: string) => (d.length === 14 ? `**.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-**` : `***.${d.slice(3, 6)}.${d.slice(6, 9)}-**`)

function filterParties(url: URL): Party[] {
  const p = url.searchParams
  let items = pld.parties
  const q = p.get('q')
  if (q) {
    const digits = onlyDigits(q)
    items = items.filter((x) => strip(x.name).includes(strip(q)) || (digits.length >= 6 && onlyDigits(x.document).includes(digits.slice(3, 6))))
  }
  if (p.get('type')) items = items.filter((x) => x.type === p.get('type'))
  if (p.get('riskLevel')) items = items.filter((x) => x.riskLevel === p.get('riskLevel'))
  if (p.get('pep')) items = items.filter((x) => !!x.pep === (p.get('pep') === 'true'))
  if (p.get('sourceType')) items = items.filter((x) => x.sourceType === p.get('sourceType'))
  return items
}

const findParty = (id: string | readonly string[] | undefined) => pld.parties.find((p) => p.id === id)
const findProcess = (id: string | readonly string[] | undefined): KycProcess | undefined => pld.bundle.kyc.find((k) => k.id === id)

export const pldHandlers = [
  /* ---------- partes ---------- */
  http.get(`${API}/parties/search`, ({ request }) => {
    const u = guard(request, READERS)
    return isResponse(u) ? u : HttpResponse.json(paginate(filterParties(new URL(request.url)), new URL(request.url)))
  }),
  http.get(`${API}/parties`, ({ request }) => {
    const u = guard(request, READERS)
    return isResponse(u) ? u : HttpResponse.json(paginate(filterParties(new URL(request.url)), new URL(request.url)))
  }),
  http.post(`${API}/parties`, async ({ request }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const b = (await request.json()) as PartyRequest
    const digits = onlyDigits(b.document ?? '')
    if (!b.name?.trim() || !digits) return err(400, 'name/document: obrigatórios')
    if (pld.parties.some((p) => p.document === maskDoc(digits))) return err(422, 'Documento já cadastrado')
    const party: Party = {
      id: `p-${String(pld.parties.length + 1).padStart(4, '0')}`, type: b.type, document: maskDoc(digits), name: b.name, tradeName: b.tradeName ?? null,
      email: b.email ? 'c***@e***.com' : null, phone: b.phone ? '(11) *****-0000' : null, riskLevel: 'LOW', birthDate: b.birthDate ?? null, nationality: b.nationality ?? null,
      occupation: b.occupation ?? null, status: 'ACTIVE', createdAt: new Date().toISOString(), sourceType: 'ONBOARDED', riskScore: 10, pep: false, pepStatus: 'NONE',
    }
    pld.parties.unshift(party)
    return HttpResponse.json(party, { status: 201 })
  }),
  http.get(`${API}/parties/:id`, ({ request, params }) => {
    const u = guard(request, READERS)
    if (isResponse(u)) return u
    const p = findParty(params.id)
    return p ? HttpResponse.json(p) : err(404, 'Parte não encontrada')
  }),
  http.put(`${API}/parties/:id`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const p = findParty(params.id)
    if (!p) return err(404, 'Parte não encontrada')
    const b = (await request.json()) as PartyRequest
    // como o backend deveria: ignora valores mascarados
    Object.assign(p, { name: b.name, tradeName: b.tradeName ?? null, occupation: b.occupation ?? p.occupation, nationality: b.nationality ?? p.nationality })
    return HttpResponse.json(p)
  }),
  http.get(`${API}/parties/:id/addresses`, ({ request, params }) => {
    const u = guard(request, READERS)
    return isResponse(u) ? u : HttpResponse.json(pld.bundle.addresses[String(params.id)] ?? [])
  }),
  http.get(`${API}/parties/:id/relationships`, ({ request, params }) => {
    const u = guard(request, READERS)
    return isResponse(u) ? u : HttpResponse.json(pld.bundle.relationships[String(params.id)] ?? [])
  }),
  http.get(`${API}/parties/:id/beneficial-owners`, ({ request, params }) => {
    const u = guard(request, READERS)
    return isResponse(u) ? u : HttpResponse.json(pld.bundle.ubo[String(params.id)] ?? [])
  }),
  http.post(`${API}/parties/:id/pep`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const p = findParty(params.id)
    if (!p) return err(404, 'Parte não encontrada')
    const b = (await request.json()) as PepRequest
    if (nonBlank(b.justification) < 20) return err(422, 'Justificativa obrigatória (mínimo de 20 caracteres)')
    Object.assign(p, { pep: true, pepStatus: b.related ? 'RELATED' : 'ACTIVE', pepCategory: b.category, pepRole: b.role ?? null, pepStartDate: b.startDate ?? null, pepEndDate: b.endDate ?? null, eddRequired: true, riskLevel: p.riskLevel === 'LOW' || p.riskLevel === 'MEDIUM' ? 'HIGH' : p.riskLevel, pepApprovalAt: null })
    return HttpResponse.json({ partyId: p.id, pep: true, pepStatus: p.pepStatus, category: b.category, role: b.role ?? null, startDate: b.startDate ?? null, endDate: b.endDate ?? null, expiryDate: null, approved: false, eddRequired: true, riskLevel: p.riskLevel })
  }),
  http.post(`${API}/parties/:id/pep/approve`, async ({ request, params }) => {
    const u = guard(request, ['SUPERVISOR'])
    if (isResponse(u)) return u
    const p = findParty(params.id)
    if (!p) return err(404, 'Parte não encontrada')
    const comment = new URLSearchParams(await request.text()).get('comment') ?? ''
    pld.pepApprovals.push({ partyId: p.id, comment, by: u.id })
    p.pepApprovalAt = new Date().toISOString()
    p.pepApprovalByName = u.name
    return HttpResponse.json({ partyId: p.id, pep: true, pepStatus: p.pepStatus ?? 'ACTIVE', category: p.pepCategory ?? null, role: p.pepRole ?? null, startDate: null, endDate: null, expiryDate: null, approved: true, eddRequired: !!p.eddRequired, riskLevel: p.riskLevel })
  }),
  http.post(`${API}/parties/:id/risk/recalculate`, ({ request, params }) => {
    const u = guard(request, ['SUPERVISOR', 'ADMIN'])
    if (isResponse(u)) return u
    const p = findParty(params.id)
    if (!p) return err(404, 'Parte não encontrada')
    p.riskCalculatedAt = new Date().toISOString()
    ;(pld.bundle.risk[p.id] ??= []).unshift({ id: `rk-${Date.now()}`, previousScore: p.riskScore ?? null, newScore: p.riskScore ?? 0, previousLevel: p.riskLevel, newLevel: p.riskLevel ?? 'LOW', reason: 'Recálculo manual', createdAt: p.riskCalculatedAt })
    return HttpResponse.json(p)
  }),
  http.get(`${API}/risk-history/party/:id`, ({ request, params }) => {
    const u = guard(request, READERS)
    if (isResponse(u)) return u
    const url = new URL(request.url)
    const items = [...(pld.bundle.risk[String(params.id)] ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    const size = Number(url.searchParams.get('size') ?? 20)
    return HttpResponse.json({ content: items.slice(0, size), totalElements: items.length, totalPages: 1, number: 0, size })
  }),
  http.get(`${API}/transactions/party/:id`, ({ request, params }) => {
    const u = guard(request, READERS)
    if (isResponse(u)) return u
    const items: Transaction[] = pld.bundle.transactions.filter((t) => t.partyId === params.id)
    return HttpResponse.json(paginate(items, new URL(request.url)))
  }),
  http.get(`${API}/screening/party/:id`, ({ request, params }) => {
    const u = guard(request, READERS)
    return isResponse(u) ? u : HttpResponse.json(pld.hits.filter((h) => h.partyId === params.id))
  }),

  /* ---------- screening ---------- */
  http.post(`${API}/screening/screen/:partyId`, ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const p = findParty(params.partyId)
    if (!p) return err(404, 'Parte não encontrada')
    const hit: ScreeningResult = { id: `s-${Date.now()}`, partyId: p.id, partyName: p.name, listCode: 'PEP', matchedName: p.name, matchedDocument: p.document, matchScore: 88, matchType: 'FUZZY', status: 'PENDING', createdAt: new Date().toISOString() }
    pld.hits.unshift(hit)
    return HttpResponse.json(hit)
  }),
  http.get(`${API}/screening/lists`, ({ request }) => {
    const u = guard(request, READERS)
    return isResponse(u) ? u : err(404, 'Não encontrado') // endpoint ainda inexistente: a UI usa o catálogo ilustrativo
  }),
  http.get(`${API}/screening`, ({ request }) => {
    const u = guard(request, READERS)
    if (isResponse(u)) return u
    const url = new URL(request.url)
    const status = url.searchParams.get('status') ?? 'PENDING'
    return HttpResponse.json(paginate(pld.hits.filter((h) => h.status === status), url))
  }),
  http.post(`${API}/screening/:id/review`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const form = new URLSearchParams(await request.text())
    const status = form.get('status') ?? ''
    const justification = form.get('justification') ?? ''
    const hit = pld.hits.find((h) => h.id === params.id)
    if (!hit) return err(404, 'Hit não encontrado')
    if (status !== 'CONFIRMED' && status !== 'FALSE_POSITIVE') return err(400, 'status inválido')
    if (nonBlank(justification) < 20) return err(422, 'Justificativa obrigatória (mínimo de 20 caracteres)')
    hit.status = status
    hit.reviewJustification = justification
    hit.reviewedAt = new Date().toISOString()
    hit.reviewedByName = u.name
    pld.reviews.push({ id: hit.id, status, justification })
    const party = findParty(hit.partyId)
    if (party && status === 'CONFIRMED' && hit.requiresAssetFreeze) {
      party.assetFreezeActive = true
      party.assetFreezeAt = hit.reviewedAt
      party.riskLevel = 'CRITICAL'
    }
    return HttpResponse.json(hit)
  }),

  /* ---------- watchlist ---------- */
  http.get(`${API}/watchlist`, ({ request }) => {
    const u = guard(request, ['AUDITOR', 'ADMIN', 'SUPERVISOR'])
    return isResponse(u) ? u : HttpResponse.json(pld.watchlist.filter((w) => w.active))
  }),
  http.post(`${API}/watchlist`, async ({ request }) => {
    const u = guard(request, ['SUPERVISOR'])
    if (isResponse(u)) return u
    const f = new URLSearchParams(await request.text())
    const doc = onlyDigits(f.get('document') ?? '')
    if (!doc || !f.get('name') || nonBlank(f.get('reason')) < 20) return err(400, 'document/name/reason: obrigatórios')
    const w = { id: `w-${Date.now()}`, document: maskDoc(doc), name: f.get('name')!, reason: f.get('reason')!, severity: f.get('severity') ?? 'HIGH', active: true, createdAt: new Date().toISOString(), expiresAt: null }
    pld.watchlist.push(w)
    return HttpResponse.json(w)
  }),
  http.post(`${API}/watchlist/:id/deactivate`, ({ request, params }) => {
    const u = guard(request, ['SUPERVISOR'])
    if (isResponse(u)) return u
    const w = pld.watchlist.find((x) => x.id === params.id)
    if (!w) return err(404, 'Registro não encontrado')
    w.active = false
    return HttpResponse.json(w)
  }),

  /* ---------- KYC ---------- */
  http.get(`${API}/kyc`, ({ request }) => {
    const u = guard(request, READERS)
    if (isResponse(u)) return u
    const url = new URL(request.url)
    const partyId = url.searchParams.get('partyId')
    const items = partyId ? pld.bundle.kyc.filter((k) => k.partyId === partyId) : pld.bundle.kyc.filter((k) => k.status === (url.searchParams.get('status') ?? 'IN_PROGRESS'))
    return HttpResponse.json(paginate(items, url))
  }),
  http.post(`${API}/kyc/onboarding/:partyId`, ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const p = findParty(params.partyId)
    if (!p) return err(404, 'Parte não encontrada')
    const k: KycProcess = { id: `k-${Date.now()}`, partyId: p.id, partyName: p.name, type: p.type === 'PJ' ? 'KYB' : 'KYC', status: 'IN_PROGRESS', riskLevel: p.riskLevel, startedAt: new Date().toISOString(), completedAt: null, expiresAt: null, createdAt: new Date().toISOString() }
    pld.bundle.kyc.unshift(k)
    pld.bundle.checklist[k.id] = []
    pld.bundle.documents[k.id] = []
    return HttpResponse.json(k, { status: 201 })
  }),
  http.post(`${API}/kyc/documents/:id/approve`, ({ request, params }) => {
    const u = guard(request, ['SUPERVISOR'])
    if (isResponse(u)) return u
    const d = Object.values(pld.bundle.documents).flat().find((x) => x.id === params.id)
    if (!d) return err(404, 'Documento não encontrado')
    d.status = 'APPROVED'
    return HttpResponse.json(d)
  }),
  http.get(`${API}/kyc/documents/:id/download`, ({ request }) => {
    const u = guard(request, READERS)
    return isResponse(u) ? u : new HttpResponse('conteudo-mock', { headers: { 'Content-Type': 'application/pdf' } })
  }),
  http.get(`${API}/kyc/documents/process/:id`, ({ request, params }) => {
    const u = guard(request, READERS)
    return isResponse(u) ? u : HttpResponse.json(pld.bundle.documents[String(params.id)] ?? [])
  }),
  http.post(`${API}/kyc/documents/:processId`, async ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const file = (await request.formData()).get('file') as File | null
    const doc = { id: `d-${Date.now()}`, processId: String(params.processId), documentType: new URL(request.url).searchParams.get('documentType') ?? 'OUTROS', fileName: file?.name ?? 'arquivo', status: 'PENDING', createdAt: new Date().toISOString() }
    ;(pld.bundle.documents[String(params.processId)] ??= []).push(doc)
    return HttpResponse.json(doc, { status: 201 })
  }),
  http.get(`${API}/kyc/checklist/process/:id`, ({ request, params }) => {
    const u = guard(request, READERS)
    return isResponse(u) ? u : HttpResponse.json(pld.bundle.checklist[String(params.id)] ?? [])
  }),
  http.post(`${API}/kyc/checklist/:id/complete`, ({ request, params }) => {
    const u = guard(request, ANALYSTS)
    if (isResponse(u)) return u
    const item = Object.values(pld.bundle.checklist).flat().find((x) => x.id === params.id)
    if (!item) return err(404, 'Item não encontrado')
    item.completed = true
    item.completedAt = new Date().toISOString()
    return HttpResponse.json(item)
  }),
  http.post(`${API}/kyc/:id/approve`, ({ request, params }) => {
    const u = guard(request, ['SUPERVISOR'])
    if (isResponse(u)) return u
    const k = findProcess(params.id)
    if (!k) return err(404, 'Processo não encontrado')
    k.status = 'APPROVED'
    k.completedAt = new Date().toISOString()
    return HttpResponse.json(k)
  }),
  http.post(`${API}/kyc/:id/reject`, ({ request, params }) => {
    const u = guard(request, ['SUPERVISOR'])
    if (isResponse(u)) return u
    const k = findProcess(params.id)
    if (!k) return err(404, 'Processo não encontrado')
    k.status = 'REJECTED'
    k.completedAt = new Date().toISOString()
    return HttpResponse.json(k)
  }),

  /* ---------- usuários (ADMIN) ---------- */
  http.get(`${API}/users`, ({ request }) => {
    const u = guard(request, ['ADMIN'])
    return isResponse(u) ? u : HttpResponse.json(paginate(pld.users, new URL(request.url)))
  }),
  http.post(`${API}/users`, async ({ request }) => {
    const u = guard(request, ['ADMIN'])
    if (isResponse(u)) return u
    const b = (await request.json()) as { username: string; email: string; name: string; password: string; roles: string[] }
    if (!b.password || b.password.length < 10) return err(400, 'password: deve ter entre 10 e 128 caracteres')
    if (pld.users.some((x) => x.username === b.username)) return err(422, 'Usuário já existe')
    const created: UserAccount = { id: `u-${Date.now()}`, username: b.username, email: b.email, name: b.name, active: true, roles: b.roles, createdAt: new Date().toISOString() }
    pld.users.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),
  http.put(`${API}/users/:id`, async ({ request, params }) => {
    const u = guard(request, ['ADMIN'])
    if (isResponse(u)) return u
    const target = pld.users.find((x) => x.id === params.id)
    if (!target) return err(404, 'Usuário não encontrado')
    Object.assign(target, (await request.json()) as Partial<UserAccount>)
    return HttpResponse.json(target)
  }),
  http.post(`${API}/users/:id/deactivate`, ({ request, params }) => {
    const u = guard(request, ['ADMIN'])
    if (isResponse(u)) return u
    const target = pld.users.find((x) => x.id === params.id)
    if (!target) return err(404, 'Usuário não encontrado')
    if (target.id === u.id) return err(422, 'Não é possível desativar o próprio usuário')
    target.active = false
    return HttpResponse.json(target)
  }),
  http.post(`${API}/users/:id/activate`, ({ request, params }) => {
    const u = guard(request, ['ADMIN'])
    if (isResponse(u)) return u
    const target = pld.users.find((x) => x.id === params.id)
    if (!target) return err(404, 'Usuário não encontrado')
    target.active = true
    return HttpResponse.json(target)
  }),
]
