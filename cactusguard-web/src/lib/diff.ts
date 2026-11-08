/** Diff de estruturas JSON (snapshots de regra, old/new da auditoria). Puro, sem efeitos colaterais. */
export type FlatMap = Record<string, unknown>

export function flatten(value: unknown, prefix = '', out: FlatMap = {}): FlatMap {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    const entries = Object.entries(value as Record<string, unknown>)
    if (entries.length === 0 && prefix) out[prefix] = {}
    for (const [k, v] of entries) flatten(v, prefix ? `${prefix}.${k}` : k, out)
  } else if (prefix) {
    out[prefix] = value
  }
  return out
}

export type DiffKind = 'added' | 'removed' | 'changed'
export interface DiffEntry {
  path: string
  kind: DiffKind
  before?: unknown
  after?: unknown
}

const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b)

/** Lista só o que mudou, ordenado por caminho. `null`/`undefined` são tratados como ausente. */
export function diffObjects(before: unknown, after: unknown): DiffEntry[] {
  const a = flatten(before ?? {})
  const b = flatten(after ?? {})
  const paths = [...new Set([...Object.keys(a), ...Object.keys(b)])].sort()
  const out: DiffEntry[] = []
  for (const path of paths) {
    const inA = a[path] !== undefined && a[path] !== null
    const inB = b[path] !== undefined && b[path] !== null
    if (!inA && inB) out.push({ path, kind: 'added', after: b[path] })
    else if (inA && !inB) out.push({ path, kind: 'removed', before: a[path] })
    else if (inA && inB && !same(a[path], b[path])) out.push({ path, kind: 'changed', before: a[path], after: b[path] })
  }
  return out
}

export function formatValue(v: unknown): string {
  if (v === undefined || v === null) return '—'
  if (typeof v === 'string') return v
  if (typeof v === 'number' || typeof v === 'boolean') return String(v)
  return JSON.stringify(v)
}
