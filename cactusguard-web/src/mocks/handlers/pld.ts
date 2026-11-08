/** Handlers MSW: partes/KYC/PEP/risco, screening, watchlist, KYC e usuários (contratos da spec 033). */
import { http, HttpResponse } from 'msw'
import type { KycProcess, Party, PartyRequest, PepRequest, PiiField, RevealRequest, ScreeningResult, ScreeningReviewRequest, Transaction, UserAccount } from '@/api/types'
import { db, REVEALED } from '../data'
import { pld } from '../pldData'
import { ANALYSTS, API, READ as READERS, SUP, err, guard, invalid, isResponse, jsonBody, nonBlank, paginate, readMultipart } from './helpers'

const REVEAL_ROLES = ['ANALYST', 'SUPERVISOR', 'AUDITOR']
const PII_FIELDS: PiiField[] = ['document', 'email', 'phone', 'motherName']

const strip = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
const onlyDigits = (s: string) => s.replace(/\D/g, '')
const maskDoc = (d: string) => (d.length === 14 ? `**.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-**` : `***.${d.slice(3, 6)}.${d.slice(6, 9)}-**`)

function filterParties(url: URL): Party[] {
  const p = url.searchParams
  let items = pld.parties
  const q = p.get('q')
  if (q) {
    const digits = onlyDigits(q)
    items = items.filter((x) => strip(x.name).includes(strip(q)) || (digits.length >= 6 && onlyDigits(x.document).includes(digits.slice(3, 6))))
  }
  const doc = p.get('document')
  if (doc) {
    const d = onlyDigits(doc)
    items = items.filter((x) => d.length >= 9 && onlyDigits(x.document).includes(d.slice(3, 6)))
  }
  const type = p.get('type') ?? p.get('personType')
  if (type) items = items.filter((x) => x.type === type)
  if (p.get('riskLevel')) items = items.filter((x) => x.riskLevel === p.get('riskLevel'))
  const pep = p.get('pep') ?? p.get('isPep')
  if (pep) items = items.filter((x) => !!x.pep === (pep === 'true'))
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
    const b = await jsonBody<PartyRequest>(request)
    const digits = onlyDigits(b.document ?? '')
    const fe = [
      ...(!b.name?.trim() ? [{ field: 'name', message: 'must not be blank' }] : []),
      ...(!digits ? [{ field: 'document', message: 'must not be blank' }] : []),
      ...(!b.type ? [{ field: 'type', message: 'must not be null' }] : []),
    ]
    if (fe.length) return invalid('Dados inválidos', fe)
    if (pld.parties.some((p) => p.document === maskDoc(digits))) return err(422, 'Documento já cadastrado')
    const party: Party = {
      id: `p-${String(pld.parties.length + 1).padStart(4, '0')}`, type: b.type!, document: maskDoc(digits), name: b.name!, tradeName: b.tradeName ?? null,
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
    const b = await jsonBody<PartyRequest>(request)
    if (!b.name?.trim()) return invalid('Dados inválidos', [{ field: 'name', message: 'must not be blank' }])
    // documento imutável: ausente/mascarado/igual é ignorado; diferente e completo => 422 DOCUMENT_IMMUTABLE
    if (b.document && !b.document.includes('*')) {
      const digits = onlyDigits(b.document)
      if (digits && maskDoc(digits) !== p.document) return err(422, 'O documento da parte não pode ser alterado', 'DOCUMENT_IMMUTABLE')
    }
    // email/phone/motherName: mascarado (`*`) ou null mantém o valor atual; "" limpa
    const keep = (incoming: string | null | undefined, current: string | null | undefined) => (incoming == null || incoming.includes('*') ? (current ?? null) : incoming === '' ? null : incoming)
    Object.assign(p, {
      name: b.name, tradeName: b.tradeName ?? null, occupation: b.occupation ?? p.occupation, nationality: b.nationality ?? p.nationality,
      email: keep(b.email, p.email), phone: keep(b.phone, p.phone), motherName: keep(b.motherName, p.motherName),
      ...(b.declaredMonthlyIncome != null ? { declaredMonthlyIncome: b.declaredMonthlyIncome } : {}),
    })
    return HttpResponse.json(p)
  }),
  http.post(`${API}/parties/:id/reveal`, async ({ request, params }) => {
    const u = guard(request, REVEAL_ROLES)
    if (isResponse(u)) return u
    const p = findParty(params.id)
    if (!p) return err(404, 'Parte não encontrada')
    const b = await jsonBody<RevealRequest>(request)
    const fields = [...(b.fields ?? []), ...(b.field ? [b.field] : [])].filter((f, i, a) => a.indexOf(f) === i)
    const reason = (b.reason ?? '').trim()
    const fe = [
      ...(reason.length < 10 || reason.length > 500 ? [{ field: 'reason', message: 'size must be between 10 and 500' }] : []),
      ...(fields.length === 0 || fields.some((f) => !PII_FIELDS.includes(f)) ? [{ field: 'field', message: 'informe field/fields: document, email, phone ou motherName' }] : []),
    ]
    if (fe.length) return invalid('Dados inválidos', fe)
    db.revealLog.push({ partyId: p.id, fields, reason, by: u.id })
    const values = Object.fromEntries(fields.map((f) => [f, REVEALED[f] ?? ''])) as Partial<Record<PiiField, string>>
    return HttpResponse.json({ field: b.field ?? null, value: b.field ? (values[b.field] ?? null) : null, values }, { headers: { 'Cache-Control': 'no-store' } })
  }),
  http.get(`${API}/parties/:id/pep`, ({ request, params }) => {
    const u = guard(request, READERS)
    if (isResponse(u)) return u
    const p = findParty(params.id)
    if (!p) return err(404, 'Parte não encontrada')
    return HttpResponse.json({
      partyId: p.id, pep: !!p.pep, pepStatus: p.pepStatus ?? 'NONE', category: p.pepCategory ?? null, role: p.pepRole ?? null, startDate: p.pepStartDate ?? null, endDate: p.pepEndDate ?? null,
      expiryDate: p.pepExpiryDate ?? null, approved: !!p.pepApprovalAt, eddRequired: !!p.eddRequired, riskLevel: p.riskLevel, approvedAt: p.pepApprovalAt ?? null, approvedByName: p.pepApprovalByName ?? null,
    })
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
    const b = await jsonBody<PepRequest>(request)
    if (nonBlank(b.justification) < 20) return err(422, 'Justificativa obrigatória (mínimo de 20 caracteres)')
    Object.assign(p, { pep: true, pepStatus: b.related ? 'RELATED' : 'ACTIVE', pepCategory: b.category, pepRole: b.role ?? null, pepStartDate: b.startDate ?? null, pepEndDate: b.endDate ?? null, eddRequired: true, riskLevel: p.riskLevel === 'LOW' || p.riskLevel === 'MEDIUM' ? 'HIGH' : p.riskLevel, pepApprovalAt: null })
    return HttpResponse.json({ partyId: p.id, pep: true, pepStatus: p.pepStatus, category: b.category, role: b.role ?? null, startDate: b.startDate ?? null, endDate: b.endDate ?? null, expiryDate: null, approved: false, eddRequired: true, riskLevel: p.riskLevel, approvedAt: null, approvedByName: null })
  }),
  http.post(`${API}/parties/:id/pep/approve`, async ({ request, params }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const p = findParty(params.id)
    if (!p) return err(404, 'Parte não encontrada')
    const comment = new URLSearchParams(await request.text()).get('comment') ?? ''
    pld.pepApprovals.push({ partyId: p.id, comment, by: u.id })
    p.pepApprovalAt = new Date().toISOString()
    p.pepApprovalByName = u.name
    return HttpResponse.json({ partyId: p.id, pep: true, pepStatus: p.pepStatus ?? 'ACTIVE', category: p.pepCategory ?? null, role: p.pepRole ?? null, startDate: null, endDate: null, expiryDate: null, approved: true, eddRequired: !!p.eddRequired, riskLevel: p.riskLevel, approvedAt: p.pepApprovalAt, approvedByName: u.name })
  }),
  http.post(`${API}/parties/:id/risk/recalculate`, ({ request, params }) => {
    const u = guard(request, ANALYSTS)
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
    const hit: ScreeningResult = {
      id: `s-${Date.now()}`, partyId: p.id, partyName: p.name, listCode: 'PEP', matchedName: p.name, matchedDocument: p.document, matchScore: 88, matchType: 'FUZZY', status: 'PENDING',
      createdAt: new Date().toISOString(), requiresAssetFreeze: false, reviewJustification: null, reviewedAt: null, reviewedByName: null,
    }
    pld.hits.unshift(hit)
    return HttpResponse.json(hit)
  }),
  http.get(`${API}/screening/lists`, ({ request }) => {
    const u = guard(request, READERS)
    return isResponse(u) ? u : HttpResponse.json(pld.lists)
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
    // JSON (a justificativa não vai à URL); query/formulário continuam aceitos por compatibilidade
    const url = new URL(request.url)
    const ct = request.headers.get('content-type') ?? ''
    const body: Partial<ScreeningReviewRequest> = ct.includes('json')
      ? await jsonBody<ScreeningReviewRequest>(request)
      : Object.fromEntries(new URLSearchParams(await request.text())) as Partial<ScreeningReviewRequest>
    const status = body.status ?? url.searchParams.get('status') ?? ''
    const justification = body.justification ?? url.searchParams.get('justification') ?? ''
    const hit = pld.hits.find((h) => h.id === params.id)
    if (!hit) return err(404, 'Hit não encontrado')
    if (status !== 'CONFIRMED' && status !== 'FALSE_POSITIVE') return err(400, 'status inválido: use CONFIRMED ou FALSE_POSITIVE', 'VALIDATION_ERROR')
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
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const f = new URLSearchParams(await request.text())
    const doc = onlyDigits(f.get('document') ?? '')
    if (!doc || !f.get('name') || nonBlank(f.get('reason')) < 20) return err(400, 'document/name/reason: obrigatórios', 'VALIDATION_ERROR')
    const w = { id: `w-${Date.now()}`, document: maskDoc(doc), name: f.get('name')!, reason: f.get('reason')!, severity: f.get('severity') ?? 'HIGH', active: true, createdAt: new Date().toISOString(), expiresAt: null }
    pld.watchlist.push(w)
    return HttpResponse.json(w)
  }),
  http.post(`${API}/watchlist/:id/deactivate`, ({ request, params }) => {
    const u = guard(request, SUP)
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
    const u = guard(request, SUP)
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
    const { file } = await readMultipart(request)
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
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const k = findProcess(params.id)
    if (!k) return err(404, 'Processo não encontrado')
    k.status = 'APPROVED'
    k.completedAt = new Date().toISOString()
    return HttpResponse.json(k)
  }),
  http.post(`${API}/kyc/:id/reject`, ({ request, params }) => {
    const u = guard(request, SUP)
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
    if (!b.password || b.password.length < 10) return invalid('Dados inválidos', [{ field: 'password', message: 'size must be between 10 and 128' }])
    if (pld.users.some((x) => x.username === b.username)) return err(422, 'Usuário já existe', 'USERNAME_IN_USE')
    const created: UserAccount = { id: `u-${Date.now()}`, username: b.username, email: b.email, name: b.name, active: true, roles: b.roles, createdAt: new Date().toISOString() }
    pld.users.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),
  http.put(`${API}/users/:id`, async ({ request, params }) => {
    const u = guard(request, ['ADMIN'])
    if (isResponse(u)) return u
    const target = pld.users.find((x) => x.id === params.id)
    if (!target) return err(404, 'Usuário não encontrado')
    const b = await jsonBody<{ name: string; email: string; roles: string[] }>(request)
    const fe = [
      ...(!b.name?.trim() ? [{ field: 'name', message: 'must not be blank' }] : []),
      ...(!b.email?.trim() ? [{ field: 'email', message: 'must not be blank' }] : []),
      ...(!b.roles || b.roles.length === 0 ? [{ field: 'roles', message: 'must not be empty' }] : []),
    ]
    if (fe.length) return invalid('Dados inválidos', fe)
    if (pld.users.some((x) => x.id !== target.id && x.email.toLowerCase() === b.email!.toLowerCase())) return err(422, 'E-mail já em uso', 'EMAIL_IN_USE')
    if (target.id === u.id && !b.roles!.includes('ADMIN')) return err(422, 'O administrador não pode remover o próprio perfil ADMIN')
    Object.assign(target, { name: b.name!.trim(), email: b.email!.trim(), roles: b.roles })
    return HttpResponse.json(target)
  }),
  http.post(`${API}/users/:id/reset-password`, async ({ request, params }) => {
    const u = guard(request, ['ADMIN'])
    if (isResponse(u)) return u
    const target = pld.users.find((x) => x.id === params.id)
    if (!target) return err(404, 'Usuário não encontrado')
    const b = await jsonBody<{ newPassword?: string }>(request)
    if (b.newPassword !== undefined && (b.newPassword.length < 10 || b.newPassword.length > 128)) {
      return invalid('Dados inválidos', [{ field: 'newPassword', message: 'size must be between 10 and 128' }])
    }
    const generated = b.newPassword === undefined
    pld.passwordResets.push({ userId: target.id, generated })
    // a senha definida pelo admin nunca é devolvida; a gerada vem UMA vez (no-store)
    return HttpResponse.json(
      { userId: target.id, temporaryPassword: generated ? `Tmp#${String(pld.passwordResets.length).padStart(2, '0')}-Xk9vQ2mLw7Rz` : null, refreshTokensRevoked: true },
      { headers: { 'Cache-Control': 'no-store' } },
    )
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
