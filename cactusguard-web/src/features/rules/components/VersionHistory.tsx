import { useId, useState } from 'react'
import type { RuleVersion } from '@/api/types'
import { diffObjects, formatValue } from '@/lib/diff'
import type { DiffKind } from '@/lib/diff'
import { formatDateTime } from '@/lib/format'

const KIND_LABEL: Record<DiffKind, string> = { added: 'Adicionado', removed: 'Removido', changed: 'Alterado' }
const KIND_CLASS: Record<DiffKind, string> = { added: 'text-success', removed: 'text-danger', changed: 'text-warning' }

export function VersionDiff({ from, to }: { from: RuleVersion; to: RuleVersion }) {
  const diff = diffObjects(from.snapshot, to.snapshot)
  if (diff.length === 0) return <p className="text-sm text-muted">Nenhuma diferença entre v{from.version} e v{to.version}.</p>
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">Diferenças entre as versões {from.version} e {to.version}</caption>
        <thead className="text-xs uppercase text-muted">
          <tr>
            <th scope="col" className="px-2 py-1">Campo</th>
            <th scope="col" className="px-2 py-1">Mudança</th>
            <th scope="col" className="px-2 py-1">v{from.version}</th>
            <th scope="col" className="px-2 py-1">v{to.version}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {diff.map((d) => (
            <tr key={d.path}>
              <td className="px-2 py-1.5"><code className="text-xs">{d.path}</code></td>
              <td className={`px-2 py-1.5 font-medium ${KIND_CLASS[d.kind]}`}>{KIND_LABEL[d.kind]}</td>
              <td className="px-2 py-1.5">{formatValue(d.before)}</td>
              <td className="px-2 py-1.5">{formatValue(d.after)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function VersionHistory({ versions }: { versions: RuleVersion[] }) {
  const uid = useId()
  const sorted = [...versions].sort((a, b) => b.version - a.version)
  const [fromV, setFromV] = useState<number | null>(null)
  const [toV, setToV] = useState<number | null>(null)

  if (sorted.length === 0) return <p className="text-sm text-muted">Sem versões registradas.</p>
  const to = sorted.find((v) => v.version === (toV ?? sorted[0]!.version))!
  const from = sorted.find((v) => v.version === (fromV ?? sorted[1]?.version ?? sorted[0]!.version))!

  return (
    <div className="space-y-4">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <caption className="sr-only">Histórico de versões</caption>
          <thead className="bg-neutral-bg text-xs uppercase text-muted">
            <tr>
              {['Versão', 'Criada por', 'Aprovada por', 'Vigência', 'Motivo'].map((h) => (
                <th key={h} scope="col" className="px-2 py-1.5">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {sorted.map((v) => (
              <tr key={v.version}>
                <td className="px-2 py-1.5 font-medium">v{v.version}</td>
                <td className="px-2 py-1.5">{v.createdByName ?? (v.createdById ? v.createdById.slice(0, 8) : '—')}</td>
                <td className="px-2 py-1.5">
                  {v.approvedByName ?? (v.approvedById ? v.approvedById.slice(0, 8) : '—')}
                  {v.approvedAt && <span className="block text-xs text-muted">{formatDateTime(v.approvedAt)}</span>}
                </td>
                <td className="px-2 py-1.5 text-xs">
                  {formatDateTime(v.effectiveFrom)} até {v.effectiveTo ? formatDateTime(v.effectiveTo) : 'vigente'}
                </td>
                <td className="px-2 py-1.5">{v.changeReason ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {sorted.length > 1 && (
        <div>
          <h3 className="mb-2 text-sm font-semibold">Comparar versões</h3>
          <div className="mb-3 flex flex-wrap gap-3">
            {[
              { id: `${uid}-from`, label: 'De', value: from.version, set: setFromV },
              { id: `${uid}-to`, label: 'Para', value: to.version, set: setToV },
            ].map((s) => (
              <div key={s.id}>
                <label htmlFor={s.id} className="mb-1 block text-xs text-muted">{s.label}</label>
                <select id={s.id} value={s.value} onChange={(e) => s.set(Number(e.target.value))} className="rounded-lg border border-line bg-surface px-2.5 py-1.5 text-sm">
                  {sorted.map((v) => (
                    <option key={v.version} value={v.version}>v{v.version}</option>
                  ))}
                </select>
              </div>
            ))}
          </div>
          <VersionDiff from={from} to={to} />
        </div>
      )}
    </div>
  )
}
