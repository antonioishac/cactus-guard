/** Formatação pt-BR. Datas sempre em America/Sao_Paulo. Nada aqui registra valores em log. */
const TZ = 'America/Sao_Paulo'

const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const num = new Intl.NumberFormat('pt-BR')
const dateTimeFmt = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short', timeZone: TZ })
const dateFmt = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeZone: TZ })
// datas "puras" (yyyy-MM-dd) não têm fuso: formatar em UTC evita voltar um dia
const dateOnlyFmt = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeZone: 'UTC' })

const EMPTY = '—'

export function formatBRL(value: number | null | undefined): string {
  return value == null ? EMPTY : brl.format(value)
}

export function formatNumber(value: number | null | undefined): string {
  return value == null ? EMPTY : num.format(value)
}

export function formatPercent(value: number | null | undefined, digits = 1): string {
  return value == null ? EMPTY : `${value.toLocaleString('pt-BR', { maximumFractionDigits: digits })}%`
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return EMPTY
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? EMPTY : dateTimeFmt.format(d).replace(',', '')
}

/** Aceita instante ISO (com hora) ou data pura yyyy-MM-dd. */
export function formatDate(value: string | null | undefined): string {
  if (!value) return EMPTY
  const dateOnly = /^\d{4}-\d{2}-\d{2}$/.test(value)
  const d = new Date(dateOnly ? `${value}T00:00:00Z` : value)
  if (Number.isNaN(d.getTime())) return EMPTY
  return (dateOnly ? dateOnlyFmt : dateFmt).format(d)
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1).replace('.', ',')} KB`
  return `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} MB`
}

/**
 * Máscara de CPF/CNPJ para exibição. Se o valor já veio mascarado da API (contém `*` ou `•`) é devolvido
 * como está; só aplica máscara a dígitos crus (defesa em profundidade).
 */
export function maskDocument(value: string | null | undefined): string {
  if (!value) return EMPTY
  if (/[*•]/.test(value)) return value
  const d = value.replace(/\D/g, '')
  if (d.length === 11) return `***.${d.slice(3, 6)}.${d.slice(6, 9)}-**`
  if (d.length === 14) return `**.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-**`
  return '*'.repeat(Math.max(d.length, 4))
}

const STATUS_LABELS: Record<string, string> = {
  OPEN: 'Aberto',
  IN_ANALYSIS: 'Em análise',
  ESCALATED: 'Escalado',
  FALSE_POSITIVE: 'Falso positivo',
  CLOSED: 'Encerrado',
  PENDING_APPROVAL: 'Aguardando aprovação',
  COMMUNICATE: 'Comunicar (aprovado)',
  ARCHIVED: 'Arquivado',
  ACTIVE: 'Ativo',
}
const SEVERITY_LABELS: Record<string, string> = {
  LOW: 'Baixa',
  MEDIUM: 'Média',
  HIGH: 'Alta',
  CRITICAL: 'Crítica',
}
const DECISION_LABELS: Record<string, string> = { COMMUNICATE: 'Comunicar', ARCHIVE: 'Arquivar' }

export const statusLabel = (s: string) => STATUS_LABELS[s] ?? s
export const severityLabel = (s: string) => SEVERITY_LABELS[s] ?? s
export const decisionLabel = (s: string) => DECISION_LABELS[s] ?? s
