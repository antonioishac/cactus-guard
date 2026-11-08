/** Validação de CPF/CNPJ com dígito verificador. Funções puras; nada é registrado em log. */

export const onlyDigits = (v: string) => v.replace(/\D/g, '')

const allEqual = (d: string) => /^(\d)\1+$/.test(d)

export function isValidCpf(value: string): boolean {
  const d = onlyDigits(value)
  if (d.length !== 11 || allEqual(d)) return false
  for (const len of [9, 10]) {
    let sum = 0
    for (let i = 0; i < len; i++) sum += Number(d[i]) * (len + 1 - i)
    const check = ((sum * 10) % 11) % 10
    if (check !== Number(d[len])) return false
  }
  return true
}

export function isValidCnpj(value: string): boolean {
  const d = onlyDigits(value)
  if (d.length !== 14 || allEqual(d)) return false
  const calc = (len: number) => {
    const weights = len === 12 ? [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2] : [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
    const sum = weights.reduce((acc, w, i) => acc + Number(d[i]) * w, 0)
    const r = sum % 11
    return r < 2 ? 0 : 11 - r
  }
  return calc(12) === Number(d[12]) && calc(13) === Number(d[13])
}

export function isValidDocument(type: 'PF' | 'PJ', value: string): boolean {
  return type === 'PF' ? isValidCpf(value) : isValidCnpj(value)
}

export function formatCpf(v: string): string {
  const d = onlyDigits(v).slice(0, 11)
  return d.replace(/(\d{3})(\d)/, '$1.$2').replace(/(\d{3})(\d)/, '$1.$2').replace(/(\d{3})(\d{1,2})$/, '$1-$2')
}

export function formatCnpj(v: string): string {
  const d = onlyDigits(v).slice(0, 14)
  return d
    .replace(/(\d{2})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1/$2')
    .replace(/(\d{4})(\d{1,2})$/, '$1-$2')
}

export const formatDocument = (type: 'PF' | 'PJ', v: string) => (type === 'PF' ? formatCpf(v) : formatCnpj(v))
