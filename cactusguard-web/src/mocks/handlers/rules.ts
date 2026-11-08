import { http, HttpResponse } from 'msw'
import type { MonitoringRule, RuleRequest, RuleVersion } from '@/api/types'
import { API, err, guard, isResponse, validJustification } from './helpers'
import { db2b } from './db2b'

const READ = ['ANALYST', 'SUPERVISOR', 'ADMIN', 'AUDITOR']
const WRITE = ['SUPERVISOR', 'ADMIN']

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

export const rulesHandlers = [
  http.get(`${API}/rules`, ({ request }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    // como o backend real: só regras ativas, a menos que `includeInactive` (proposta) seja enviado
    const all = new URL(request.url).searchParams.get('includeInactive') === 'true'
    return HttpResponse.json(db2b.rules.filter((r) => all || r.active))
  }),
  http.get(`${API}/rules/:code/versions`, ({ request, params }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    return HttpResponse.json(db2b.versions[String(params.code)] ?? [])
  }),
  http.post(`${API}/rules/engine/evaluate`, ({ request }) => {
    const u = guard(request, WRITE)
    if (isResponse(u)) return u
    return HttpResponse.json({ status: 'completed', alertsGenerated: 3 })
  }),
  http.post(`${API}/rules`, async ({ request }) => {
    const u = guard(request, WRITE)
    if (isResponse(u)) return u
    const b = (await request.json()) as RuleRequest
    if (!b.code || findRule(b.code)) return err(422, 'Código inválido ou já existente')
    const r: MonitoringRule = {
      id: `r-${b.code.toLowerCase()}`, code: b.code, name: b.name, description: null, category: b.category, severity: b.severity, active: false, status: 'PENDING_APPROVAL',
      version: 1, parameters: null, effectiveFrom: null, effectiveTo: null, thresholdValue: null, thresholdPeriodDays: null, createdById: u.id, createdByName: u.name,
    }
    applyRequest(r, b)
    db2b.rules.push(r)
    db2b.versions[r.code] = []
    return HttpResponse.json(r, { status: 201 })
  }),
  http.put(`${API}/rules/:code`, async ({ request, params }) => {
    const u = guard(request, WRITE)
    if (isResponse(u)) return u
    const r = findRule(params.code)
    if (!r) return err(404, 'Regra não encontrada')
    const b = (await request.json()) as RuleRequest
    if (!validJustification(b.reason)) return err(422, 'Justificativa obrigatória (mínimo de 20 caracteres) para alterar regra')
    applyRequest(r, b)
    r.status = 'PENDING_APPROVAL'
    r.active = false
    r.version += 1
    r.createdById = u.id
    r.createdByName = u.name
    return HttpResponse.json(r)
  }),
  http.post(`${API}/rules/:code/approve`, ({ request, params }) => {
    const u = guard(request, ['SUPERVISOR'])
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
      approvedById: u.id, approvedByName: u.name, createdById: r.createdById ?? null, createdByName: r.createdByName ?? null, approvedAt: now, effectiveFrom: now, effectiveTo: null,
    }
    versions.push(v)
    r.status = 'ACTIVE'
    r.active = true
    r.effectiveFrom = now
    r.effectiveTo = null
    return HttpResponse.json(r)
  }),
  http.patch(`${API}/rules/:code/toggle`, ({ request, params }) => {
    const u = guard(request, WRITE)
    if (isResponse(u)) return u
    const r = findRule(params.code)
    if (!r) return err(404, 'Regra não encontrada')
    const p = new URL(request.url).searchParams
    if (!validJustification(p.get('reason'))) return err(422, 'Justificativa obrigatória (mínimo de 20 caracteres)')
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
    }
    return HttpResponse.json(r)
  }),
]
