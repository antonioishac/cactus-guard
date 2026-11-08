import type { PageParams } from './types'

/** Remove chaves vazias para não enviar `status=` ao backend. */
export function cleanParams<T extends object>(p: T): Record<string, string | number> {
  const out: Record<string, string | number> = {}
  for (const [k, v] of Object.entries(p)) {
    if (v !== undefined && v !== null && v !== '') out[k] = v as string | number
  }
  return out
}

export const DEFAULT_PAGE_SIZE = 20

export function pageQuery(p: PageParams): PageParams {
  return { page: p.page ?? 0, size: p.size ?? DEFAULT_PAGE_SIZE, ...(p.sort ? { sort: p.sort } : {}) }
}
