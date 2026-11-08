import { http, HttpResponse } from 'msw'
import type { MonitoringRule, RuleRequest, RuleVersion } from '@/api/types'
import { db } from '../data'
import { API, READ, SUP, err, guard, invalid, isResponse, jsonBody, multi, paginate, parseDay, validJustification, JUSTIFICATION_MSG } from './helpers'
import { db2b } from './db2b'

const WRITE = ['SUPERVISOR', 'ADMIN']
const D = 86_400_000

const findRule = (code: unknown) => db2b.rules.find((r) => r.code === code)

function applyRequest(r: MonitoringRule, b: RuleRequest) {
  r.name = b.name
  r.description = b.description ?? null
  r.category = b.category
  r.severity = b.severity
  r.thresholdValue = b.thresholdValue ?? null
  r.thresholdPeriodDays = b.thresholdPeriodDays ?? null
  r.parameters = b.parameters ?? null
  r.appliesToRiskLevels = b.appliesToRiskLevels ?? null
  r.carta4001Reference = b.carta4001Reference ?? null
}

/** Métricas dos últimos 30 dias: alertas da regra no mock + taxa de falso positivo. */
function metricsOf(code: string) {
  const mine = db.alerts.filter((a) => a.ruleCode === code)
  const closed = mine.filter((a) => ['FALSE_POSITIVE', 'CLOSED'].includes(a.status))
  const fp = closed.filter((a) => a.status === 'FALSE_POSITIVE').length
  const base = findRule(code)
  const generated = base?.alertsGenerated ?? mine.length
  return { alertsGenerated: generated, alertsClosed: closed.length, falsePositives: fp, falsePositiveRate: closed.length ? fp / closed.length : null }
}

export const rulesHandlers = [
  http.get(`${API}/rules`, ({ request }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    const url = new URL(request.url)
    const p = url.searchParams
    const status = multi(url, 'status')
    const includeInactive = p.get('includeInactive') === 'true'
    const q = p.get('q')?.trim().toLowerCase()
    let items = db2b.rules
    // padrão = só ativas; includeInactive inclui todas; `status` prevalece
    if (status.length) items = items.filter((r) => status.includes(r.status))
    else if (!includeInactive) items = items.filter((r) => r.active)
    if (p.get('category')) items = items.filter((r) => r.category === p.get('category'))
    if (p.get('severity')) items = items.filter((r) => r.severity === p.get('severity'))
    if (q) items = items.filter((r) => r.code.toLowerCase().includes(q) || r.name.toLowerCase().includes(q))
    return HttpResponse.json(paginate(items, url))
  }),
  http.get(`${API}/rules/:code/versions`, ({ request, params }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    if (!findRule(params.code)) return err(404, 'Regra não encontrada')
    return HttpResponse.json(db2b.versions[String(params.code)] ?? [])
  }),
  http.get(`${API}/rules/:code/metrics`, ({ request, params }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    if (!findRule(params.code)) return err(404, 'Regra não encontrada')
    const p = new URL(request.url).searchParams
    const from = parseDay(p.get('from'), false)
    const to = parseDay(p.get('to'), true)
    if (from === null || to === null) return err(400, 'Período inválido', 'VALIDATION_ERROR')
    const now = Date.now()
    return HttpResponse.json({ code: String(params.code), from: new Date(from ?? now - 30 * D).toISOString(), to: new Date(to ?? now).toISOString(), ...metricsOf(String(params.code)) })
  }),
  http.get(`${API}/rules/:code`, ({ request, params }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    const r = findRule(params.code)
    return r ? HttpResponse.json(r) : err(404, `Regra não encontrada: ${String(params.code)}`)
  }),
  http.post(`${API}/rules/engine/evaluate`, ({ request }) => {
    const u = guard(request, WRITE)
    if (isResponse(u)) return u
    return HttpResponse.json({ status: 'completed', alertsGenerated: 3 })
  }),
  http.post(`${API}/rules`, async ({ request }) => {
    const u = guard(request, WRITE)
    if (isResponse(u)) return u
    const b = await jsonBody<RuleRequest>(request)
    const fe = [
      ...(!b.name?.trim() ? [{ field: 'name', message: 'must not be blank' }] : []),
      ...(!b.category ? [{ field: 'category', message: 'must not be blank' }] : []),
      ...(!b.severity ? [{ field: 'severity', message: 'must not be blank' }] : []),
    ]
    if (fe.length) return invalid('Dados inválidos', fe)
    if (!b.code || findRule(b.code)) return err(422, 'Código inválido ou já existente')
    const r: MonitoringRule = {
      id: `r-${b.code.toLowerCase()}`, code: b.code, name: b.name!, description: null, category: b.category!, severity: b.severity!, active: false, status: 'PENDING_APPROVAL',
      version: 1, parameters: null, effectiveFrom: null, effectiveTo: null, thresholdValue: null, thresholdPeriodDays: null, createdById: u.id, createdByName: u.name,
      approvedById: null, approvedByName: null, approvedAt: null, carta4001Reference: null, appliesToRiskLevels: null, autoCommunication: false, activeVersion: null, alertsGenerated: 0, falsePositiveRate: null,
    }
    applyRequest(r, b as RuleRequest)
    db2b.rules.push(r)
    db2b.versions[r.code] = []
    return HttpResponse.json(r, { status: 201 })
  }),
  http.put(`${API}/rules/:code`, async ({ request, params }) => {
    const u = guard(request, WRITE)
    if (isResponse(u)) return u
    const r = findRule(params.code)
    if (!r) return err(404, 'Regra não encontrada')
    const b = await jsonBody<RuleRequest>(request)
    if (!validJustification(b.reason)) return err(422, 'Justificativa obrigatória (mínimo de 20 caracteres) para alterar regra')
    applyRequest(r, b as RuleRequest)
    r.status = 'PENDING_APPROVAL'
    r.active = false
    r.version += 1
    r.createdById = u.id
    r.createdByName = u.name
    return HttpResponse.json(r)
  }),
  http.post(`${API}/rules/:code/approve`, ({ request, params }) => {
    const u = guard(request, SUP)
    if (isResponse(u)) return u
    const r = findRule(params.code)
    if (!r) return err(404, 'Regra não encontrada')
    if (r.status !== 'PENDING_APPROVAL') return err(422, `Regra nao esta aguardando aprovacao (status ${r.status})`)
    if (r.createdById === u.id) return err(422, 'Aprovacao deve ser feita por usuario diferente de quem criou/alterou a regra')
    const now = new Date().toISOString()
    const versions = (db2b.versions[r.code] ??= [])
    versions.forEach((v) => (v.effectiveTo ??= now))
    const v: RuleVersion = {
      version: r.version, snapshot: { code: r.code, name: r.name, severity: r.severity, version: r.version, parameters: r.parameters }, changeReason: new URL(request.url).searchParams.get('comment'),
      approvedById: u.id, approvedByName: u.name, createdById: r.createdById, createdByName: r.createdByName, approvedAt: now, effectiveFrom: now, effectiveTo: null,
    }
    versions.push(v)
    r.status = 'ACTIVE'
    r.active = true
    r.effectiveFrom = now
    r.effectiveTo = null
    r.approvedById = u.id
    r.approvedByName = u.name
    r.approvedAt = now
    r.activeVersion = r.version
    return HttpResponse.json(r)
  }),
  http.patch(`${API}/rules/:code/toggle`, ({ request, params }) => {
    const u = guard(request, WRITE)
    if (isResponse(u)) return u
    const r = findRule(params.code)
    if (!r) return err(404, 'Regra não encontrada')
    const p = new URL(request.url).searchParams
    if (!validJustification(p.get('reason'))) return err(422, JUSTIFICATION_MSG)
    if (p.get('active') === 'true') {
      if (!['INACTIVE', 'DRAFT'].includes(r.status)) return err(422, `Reativacao so se aplica a regra INACTIVE/DRAFT (status ${r.status})`)
      r.status = 'PENDING_APPROVAL'
      r.createdById = u.id
      r.createdByName = u.name
    } else {
      if (r.status !== 'ACTIVE') return err(422, `Somente regra ACTIVE pode ser desativada (status ${r.status})`)
      r.status = 'INACTIVE'
      r.active = false
      r.effectiveTo = new Date().toISOString()
      r.activeVersion = null
    }
    return HttpResponse.json(r)
  }),
]
