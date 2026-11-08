import type { ParamValue, RuleParams } from '@/api/types'

/**
 * Editor de parâmetros gerado a partir do JSON da regra. Cada folha vira um campo tipado:
 * número, texto, booleano ou lista de textos/números (separados por vírgula). Objetos aninhados são achatados
 * em caminhos `a.b` e reconstruídos ao salvar.
 */
export type FieldKind = 'number' | 'string' | 'boolean' | 'list'

export interface ParamField {
  path: string
  kind: FieldKind
  /** valor como texto de formulário (booleano = "true"/"false") */
  value: string
  /** lista de números? (decide a conversão ao salvar) */
  numericList?: boolean
}

function kindOf(v: ParamValue): FieldKind {
  if (typeof v === 'number') return 'number'
  if (typeof v === 'boolean') return 'boolean'
  if (Array.isArray(v)) return 'list'
  return 'string'
}

export function paramsToFields(params: RuleParams | null | undefined, prefix = ''): ParamField[] {
  const out: ParamField[] = []
  for (const [k, v] of Object.entries(params ?? {})) {
    const path = prefix ? `${prefix}.${k}` : k
    if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
      out.push(...paramsToFields(v as RuleParams, path))
    } else {
      const kind = kindOf(v)
      out.push({
        path,
        kind,
        value: kind === 'list' ? (v as ParamValue[]).map(String).join(', ') : v === null ? '' : String(v),
        numericList: kind === 'list' && (v as ParamValue[]).length > 0 && (v as ParamValue[]).every((x) => typeof x === 'number'),
      })
    }
  }
  return out
}

/** Erros por caminho. Vazio = válido. */
export function validateFields(fields: ParamField[]): Record<string, string> {
  const errors: Record<string, string> = {}
  const seen = new Set<string>()
  for (const f of fields) {
    if (!f.path.trim()) errors[f.path] = 'Informe o nome do parâmetro.'
    else if (!/^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)*$/.test(f.path)) errors[f.path] = 'Use apenas letras, números e "_".'
    else if (seen.has(f.path)) errors[f.path] = 'Parâmetro duplicado.'
    seen.add(f.path)
    if (errors[f.path]) continue
    if (f.kind === 'number') {
      const n = Number(f.value.replace(',', '.'))
      if (f.value.trim() === '' || !Number.isFinite(n)) errors[f.path] = 'Informe um número válido.'
      else if (n < 0) errors[f.path] = 'O valor não pode ser negativo.'
    } else if (f.kind === 'string' && f.value.trim() === '') {
      errors[f.path] = 'Campo obrigatório.'
    } else if (f.kind === 'list') {
      const items = f.value.split(',').map((s) => s.trim())
      if (items.length === 0 || items.some((s) => s === '')) errors[f.path] = 'Informe itens separados por vírgula, sem vazios.'
      else if (f.numericList && items.some((s) => !Number.isFinite(Number(s)))) errors[f.path] = 'Todos os itens devem ser números.'
    }
  }
  return errors
}

export function fieldsToParams(fields: ParamField[]): RuleParams {
  const root: RuleParams = {}
  for (const f of fields) {
    const parts = f.path.split('.')
    let node = root
    parts.slice(0, -1).forEach((p) => {
      const next = node[p]
      if (next === undefined || next === null || typeof next !== 'object' || Array.isArray(next)) node[p] = {}
      node = node[p] as RuleParams
    })
    const last = parts[parts.length - 1]!
    node[last] =
      f.kind === 'number'
        ? Number(f.value.replace(',', '.'))
        : f.kind === 'boolean'
          ? f.value === 'true'
          : f.kind === 'list'
            ? f.value.split(',').map((s) => s.trim()).map((s) => (f.numericList ? Number(s) : s))
            : f.value.trim()
  }
  return root
}
