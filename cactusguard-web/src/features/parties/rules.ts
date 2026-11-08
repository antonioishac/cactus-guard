import type { Party } from '@/api/types'
import type { Tone } from '@/components/ui/Badge'

/** Regras de APRESENTAÇÃO da ficha. O front não calcula risco, PEP nem EDD (spec 026): só destaca o que a API informa. */

const IRREGULAR_RECEITA = ['TITULAR_FALECIDO', 'SUSPENSA', 'CANCELADA', 'INAPTA', 'BAIXADA', 'NULA']

const RECEITA_LABEL: Record<string, string> = {
  REGULAR: 'Regular',
  ATIVA: 'Ativa',
  PENDENTE: 'Pendente de regularização',
  TITULAR_FALECIDO: 'Titular falecido',
  SUSPENSA: 'Suspensa',
  CANCELADA: 'Cancelada',
  INAPTA: 'Inapta',
  BAIXADA: 'Baixada',
  NULA: 'Nula',
}

export const normalizeReceita = (s: string | null | undefined) =>
  (s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toUpperCase().replace(/\s+/g, '_')

export const isReceitaIrregular = (s: string | null | undefined) => IRREGULAR_RECEITA.includes(normalizeReceita(s))
export const receitaLabel = (s: string | null | undefined) => (s ? (RECEITA_LABEL[normalizeReceita(s)] ?? s) : '—')

export const RISK_TONE: Record<string, Tone> = { CRITICAL: 'danger', HIGH: 'orange', MEDIUM: 'warning', LOW: 'success' }
export const RISK_LABEL: Record<string, string> = { CRITICAL: 'Crítico', HIGH: 'Alto', MEDIUM: 'Médio', LOW: 'Baixo' }
export const riskLabel = (r: string | null | undefined) => (r ? (RISK_LABEL[r] ?? r) : '—')

export const PEP_STATUS_LABEL: Record<string, string> = {
  ACTIVE: 'PEP ativo',
  EXPIRED: 'PEP expirado (mais de 5 anos fora do cargo)',
  RELATED: 'Relacionado a PEP',
  NONE: 'Não PEP',
}
export const PEP_CATEGORY_LABEL: Record<string, string> = {
  NATIONAL: 'Nacional',
  FOREIGN: 'Estrangeiro',
  INTERNATIONAL_ORG: 'Organização internacional',
}

export const isPepActive = (p: Pick<Party, 'pep' | 'pepStatus'>) => !!p.pep || p.pepStatus === 'ACTIVE' || p.pepStatus === 'RELATED'

export const SOURCE_LABEL: Record<string, string> = { ONBOARDED: 'Onboarding', MIGRATED: 'Migrada' }
export const COMPANY_SIZE_LABEL: Record<string, string> = { MEI: 'MEI', ME: 'Microempresa', EPP: 'Pequeno porte', MEDIO: 'Médio porte', GRANDE: 'Grande porte' }

const RECENT_COMPANY_DAYS = 365

/** Empresa aberta há menos de 1 ano (indício de fachada quando movimenta muito; CC4001 grupos III/IV). */
export function isRecentCompany(incorporationDate: string | null | undefined, now = Date.now()): boolean {
  if (!incorporationDate) return false
  const t = new Date(`${incorporationDate}T00:00:00Z`).getTime()
  return !Number.isNaN(t) && now - t < RECENT_COMPANY_DAYS * 86_400_000
}

export const directionLabel = (d: string | null | undefined) =>
  d === 'CREDIT' || d === 'IN' ? 'Crédito (entrada)' : d === 'DEBIT' || d === 'OUT' ? 'Débito (saída)' : '—'

/** Percentual efetivo = produto dos percentuais da cadeia (cada elo em %). */
export function effectivePct(pcts: (number | null)[]): number | null {
  if (pcts.length === 0 || pcts.some((p) => p == null)) return null
  return pcts.reduce<number>((acc, p) => (acc * (p as number)) / 100, 100)
}
