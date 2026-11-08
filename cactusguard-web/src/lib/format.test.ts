import { describe, expect, it } from 'vitest'
import { evaluateDeadline } from './deadline'
import { formatBRL, formatDate, formatDateTime, maskDocument } from './format'

const nbsp = (s: string) => s.split(String.fromCharCode(160)).join(' ')

describe('formatação pt-BR', () => {
  it('formata moeda BRL', () => {
    expect(nbsp(formatBRL(1234.5))).toBe('R$ 1.234,50')
    expect(formatBRL(null)).toBe('—')
  })

  it('formata data/hora em America/Sao_Paulo', () => {
    // 02:30 UTC = 23:30 do dia anterior em São Paulo (UTC-3)
    expect(formatDateTime('2026-11-08T02:30:00Z')).toBe('07/11/2026 23:30')
  })

  it('não desloca datas puras (yyyy-MM-dd)', () => {
    expect(formatDate('2026-01-01')).toBe('01/01/2026')
    expect(formatDate(null)).toBe('—')
  })
})

describe('mascaramento de documentos', () => {
  it('mascara CPF e CNPJ crus', () => {
    expect(maskDocument('12345678909')).toBe('***.456.789-**')
    expect(maskDocument('12345678000195')).toBe('**.345.678/0001-**')
  })

  it('mantém o que já veio mascarado da API', () => {
    expect(maskDocument('***.456.789-**')).toBe('***.456.789-**')
  })

  it('nunca devolve dígitos desconhecidos em claro', () => {
    expect(maskDocument('123')).not.toContain('123')
  })
})

describe('classificação provisória de prazos', () => {
  const now = Date.parse('2026-11-08T12:00:00Z')
  const H = 3_600_000
  it('ok / warning / overdue / done', () => {
    expect(evaluateDeadline({ deadline: new Date(now + 10 * 24 * H).toISOString(), warnMs: 5 * 24 * H, now }).state).toBe('ok')
    expect(evaluateDeadline({ deadline: new Date(now + 2 * 24 * H).toISOString(), warnMs: 5 * 24 * H, now }).state).toBe('warning')
    expect(evaluateDeadline({ deadline: new Date(now - H).toISOString(), warnMs: 5 * 24 * H, now }).state).toBe('overdue')
    const done = evaluateDeadline({ deadline: new Date(now - H).toISOString(), doneAt: new Date(now).toISOString(), warnMs: H, now })
    expect(done).toMatchObject({ state: 'done', late: true })
    expect(evaluateDeadline({ deadline: null, warnMs: H, now }).state).toBe('none')
  })
})
