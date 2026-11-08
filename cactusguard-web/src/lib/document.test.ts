import { describe, expect, it } from 'vitest'
import { formatCnpj, formatCpf, isValidCnpj, isValidCpf, isValidDocument } from './document'

describe('validação de CPF', () => {
  it('aceita CPF válido, com ou sem máscara', () => {
    expect(isValidCpf('529.982.247-25')).toBe(true)
    expect(isValidCpf('52998224725')).toBe(true)
  })
  it('rejeita dígito verificador errado, tamanho errado e repetidos', () => {
    expect(isValidCpf('529.982.247-24')).toBe(false)
    expect(isValidCpf('1234')).toBe(false)
    expect(isValidCpf('111.111.111-11')).toBe(false)
  })
})

describe('validação de CNPJ', () => {
  it('aceita CNPJ válido', () => {
    expect(isValidCnpj('11.222.333/0001-81')).toBe(true)
    expect(isValidCnpj('11222333000181')).toBe(true)
  })
  it('rejeita dígito verificador errado e repetidos', () => {
    expect(isValidCnpj('11.222.333/0001-82')).toBe(false)
    expect(isValidCnpj('00000000000000')).toBe(false)
  })
  it('isValidDocument escolhe pelo tipo', () => {
    expect(isValidDocument('PF', '52998224725')).toBe(true)
    expect(isValidDocument('PJ', '52998224725')).toBe(false)
  })
})

describe('máscaras', () => {
  it('formata CPF e CNPJ', () => {
    expect(formatCpf('52998224725')).toBe('529.982.247-25')
    expect(formatCnpj('11222333000181')).toBe('11.222.333/0001-81')
  })
})
