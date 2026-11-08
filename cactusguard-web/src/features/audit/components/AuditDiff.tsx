import { diffObjects, formatValue } from '@/lib/diff'
import type { DiffKind } from '@/lib/diff'

const LABEL: Record<DiffKind, string> = { added: 'Adicionado', removed: 'Removido', changed: 'Alterado' }
const CLASS: Record<DiffKind, string> = { added: 'text-success', removed: 'text-danger', changed: 'text-warning' }

/** Diff old/new de uma entrada de auditoria. Valores exibidos como a API devolve (já mascarados). */
export function AuditDiff({ oldValue, newValue }: { oldValue?: Record<string, unknown> | null; newValue?: Record<string, unknown> | null }) {
  const rows = diffObjects(oldValue, newValue)
  if (rows.length === 0) return <p className="text-sm text-muted">Sem alterações de valores registradas.</p>
  return (
    <table className="w-full text-left text-sm">
      <caption className="sr-only">Alterações registradas</caption>
      <thead className="text-xs uppercase text-muted">
        <tr>
          <th scope="col" className="px-2 py-1">Campo</th>
          <th scope="col" className="px-2 py-1">Mudança</th>
          <th scope="col" className="px-2 py-1">Antes</th>
          <th scope="col" className="px-2 py-1">Depois</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-line">
        {rows.map((r) => (
          <tr key={r.path}>
            <td className="px-2 py-1"><code className="text-xs">{r.path}</code></td>
            <td className={`px-2 py-1 font-medium ${CLASS[r.kind]}`}>{LABEL[r.kind]}</td>
            <td className="px-2 py-1 break-all">{formatValue(r.before)}</td>
            <td className="px-2 py-1 break-all">{formatValue(r.after)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
