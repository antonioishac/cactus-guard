import { formatValue } from '@/lib/diff'

/** Payload regulatório em somente leitura (o front não monta nem valida o conteúdo — spec 030). */
function Node({ value }: { value: unknown }) {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    const entries = Object.entries(value as Record<string, unknown>)
    if (entries.length === 0) return <span className="text-muted">—</span>
    return (
      <dl className="grid gap-x-4 gap-y-2 sm:grid-cols-2">
        {entries.map(([k, v]) => (
          <div key={k} className={typeof v === 'object' && v !== null && !Array.isArray(v) ? 'sm:col-span-2' : ''}>
            <dt className="text-xs text-muted">{humanize(k)}</dt>
            <dd className="break-words text-sm">
              <Node value={v} />
            </dd>
          </div>
        ))}
      </dl>
    )
  }
  if (Array.isArray(value)) return <>{value.length === 0 ? '—' : value.map(formatValue).join(', ')}</>
  return <>{formatValue(value)}</>
}

function humanize(key: string): string {
  const spaced = key.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim()
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

export function PayloadView({ payload }: { payload: Record<string, unknown> | null }) {
  if (!payload || Object.keys(payload).length === 0) return <p className="text-sm text-muted">Sem conteúdo no payload.</p>
  return (
    <div aria-label="Conteúdo da comunicação (somente leitura)">
      <Node value={payload} />
    </div>
  )
}
