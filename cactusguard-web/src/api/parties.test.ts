import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import { loginAs } from '@/test/utils'
import { toApiError } from './errors'
import { getPep, listParties, revealPartyField, updateParty } from './parties'
import { resetUserPassword } from './users'
import { listScreeningLists, reviewScreening } from './screening'

describe('contratos de partes (MSW espelhando o backend)', () => {
  it('filtros combináveis: type, riskLevel, pep, sourceType (server-side)', async () => {
    loginAs('u-analyst')
    const res = await listParties({ type: 'PF', riskLevel: 'HIGH', pep: true }, { size: 50 })
    expect(res.content.length).toBeGreaterThan(0)
    expect(res.content.every((p) => p.type === 'PF' && p.riskLevel === 'HIGH' && p.pep)).toBe(true)
  })

  it('PUT ignora valores mascarados e preserva e-mail/telefone/documento', async () => {
    loginAs('u-analyst')
    const before = (await listParties({ q: 'João da Silva' }, {})).content[0]!
    const after = await updateParty(before.id, { type: 'PF', name: 'João da Silva Santos Jr', email: before.email, phone: before.phone, motherName: before.motherName })
    expect(after.name).toBe('João da Silva Santos Jr')
    expect(after.email).toBe(before.email)
    expect(after.phone).toBe(before.phone)
    expect(after.document).toBe(before.document)
  })

  it('documento diferente e completo => 422 DOCUMENT_IMMUTABLE; mascarado é ignorado', async () => {
    loginAs('u-analyst')
    const p = (await listParties({ q: 'João da Silva' }, {})).content[0]!
    await expect(updateParty(p.id, { type: 'PF', name: p.name, document: '529.982.247-25' })).rejects.toMatchObject({ status: 422, code: 'DOCUMENT_IMMUTABLE' })
    await expect(updateParty(p.id, { type: 'PF', name: p.name, document: p.document })).resolves.toMatchObject({ id: p.id })
  })

  it('reveal: vários campos, motivo 10–500, ADMIN 403 e sem cache', async () => {
    loginAs('u-analyst')
    const p = (await listParties({ q: 'João da Silva' }, {})).content[0]!
    const ok = await revealPartyField(p.id, { fields: ['document', 'email'], reason: 'Conferência cadastral' })
    expect(ok.values).toEqual({ document: '123.456.789-09', email: 'joao.santos@exemplo.com' })
    await expect(revealPartyField(p.id, { field: 'document', reason: 'curto' })).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
      fieldErrors: [{ field: 'reason', message: expect.any(String) }],
    })
    loginAs('u-admin')
    await expect(revealPartyField(p.id, { field: 'document', reason: 'Tentativa do administrador' })).rejects.toMatchObject({ status: 403, code: 'FORBIDDEN' })
  })

  it('GET /parties/{id}/pep devolve a situação PEP', async () => {
    loginAs('u-analyst')
    const p = (await listParties({ q: 'João da Silva' }, {})).content[0]!
    expect(await getPep(p.id)).toMatchObject({ partyId: p.id, pep: true, category: 'NATIONAL', approved: false })
  })

  it('screening: review em JSON e catálogo real', async () => {
    loginAs('u-analyst')
    expect((await listScreeningLists()).some((l) => l.code === 'CSNU' && l.requiresAssetFreeze)).toBe(true)
    const r = await reviewScreening('s-3', { status: 'CONFIRMED', justification: 'Homônimo descartado: documento e data de nascimento divergem.' })
    expect(r).toMatchObject({ status: 'CONFIRMED', reviewedByName: 'Ana Martins', requiresAssetFreeze: true })
    await expect(reviewScreening('s-4', { status: 'FALSE_POSITIVE', justification: 'curta' })).rejects.toMatchObject({ status: 422, code: 'BUSINESS_RULE' })
  })

  it('reset de senha: gerada vem uma vez; definida nunca é devolvida', async () => {
    loginAs('u-admin')
    expect((await resetUserPassword('u-analyst')).temporaryPassword).toBeTruthy()
    expect(await resetUserPassword('u-analyst', { newPassword: 'SenhaForte#2026' })).toMatchObject({ temporaryPassword: null, refreshTokensRevoked: true })
    await expect(resetUserPassword('u-analyst', { newPassword: 'curta' })).rejects.toMatchObject({ status: 400, code: 'VALIDATION_ERROR' })
  })

  it('toApiError lê code, traceId (corpo e X-Trace-Id) e fieldErrors', async () => {
    loginAs('u-analyst')
    server.use(
      http.get('*/api/v1/parties', () =>
        HttpResponse.json({ status: 429, code: 'RATE_LIMITED', message: 'Muitas requisições' }, { status: 429, headers: { 'X-Trace-Id': 'hdr-trace-1' } }),
      ),
    )
    const err = await listParties({}, {}).catch((e: unknown) => toApiError(e))
    expect(err).toMatchObject({ status: 429, code: 'RATE_LIMITED', traceId: 'hdr-trace-1', fieldErrors: [] })
  })
})
