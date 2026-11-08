import { useId, useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import type { FieldKind, ParamField } from '../params'

const CONTROL = 'w-full rounded-lg border border-line bg-surface px-2.5 py-1.5 text-sm'
const KIND_LABEL: Record<FieldKind, string> = { number: 'Número', string: 'Texto', boolean: 'Sim/Não', list: 'Lista' }

interface Props {
  fields: ParamField[]
  errors: Record<string, string>
  onChange: (fields: ParamField[]) => void
}

/** Formulário gerado a partir do JSON de parâmetros. Validação em `validateFields` (params.ts). */
export function ParamsEditor({ fields, errors, onChange }: Props) {
  const uid = useId()
  const [newPath, setNewPath] = useState('')
  const [newKind, setNewKind] = useState<FieldKind>('number')

  const patch = (i: number, p: Partial<ParamField>) => onChange(fields.map((f, idx) => (idx === i ? { ...f, ...p } : f)))

  return (
    <fieldset className="space-y-3">
      <legend className="mb-1 text-sm font-medium">Parâmetros</legend>
      {fields.length === 0 && <p className="text-sm text-muted">Nenhum parâmetro. Adicione abaixo, se a regra exigir.</p>}
      {fields.map((f, i) => {
        const id = `${uid}-${i}`
        const err = errors[f.path]
        return (
          <div key={f.path + i} className="grid items-start gap-2 sm:grid-cols-[1fr_2fr_auto]">
            <label htmlFor={id} className="pt-1.5 text-sm">
              <code className="text-xs">{f.path}</code>
              <span className="ml-1 text-xs text-muted">({KIND_LABEL[f.kind]})</span>
            </label>
            <div>
              {f.kind === 'boolean' ? (
                <select id={id} value={f.value} onChange={(e) => patch(i, { value: e.target.value })} className={CONTROL}>
                  <option value="true">Sim</option>
                  <option value="false">Não</option>
                </select>
              ) : (
                <input
                  id={id}
                  value={f.value}
                  inputMode={f.kind === 'number' ? 'decimal' : undefined}
                  onChange={(e) => patch(i, { value: e.target.value })}
                  aria-invalid={!!err}
                  aria-describedby={err ? `${id}-err` : undefined}
                  placeholder={f.kind === 'list' ? 'item1, item2' : undefined}
                  className={CONTROL}
                />
              )}
              {err && (
                <p id={`${id}-err`} role="alert" className="mt-1 text-xs text-danger">
                  {err}
                </p>
              )}
            </div>
            <Button variant="ghost" className="!px-2" aria-label={`Remover parâmetro ${f.path}`} onClick={() => onChange(fields.filter((_, idx) => idx !== i))}>
              <Trash2 size={14} />
            </Button>
          </div>
        )
      })}

      <div className="flex flex-wrap items-end gap-2 border-t border-line pt-3">
        <div>
          <label htmlFor={`${uid}-new`} className="mb-1 block text-xs text-muted">Novo parâmetro (ex.: window_days)</label>
          <input id={`${uid}-new`} value={newPath} onChange={(e) => setNewPath(e.target.value)} className={CONTROL} />
        </div>
        <div>
          <label htmlFor={`${uid}-kind`} className="mb-1 block text-xs text-muted">Tipo</label>
          <select id={`${uid}-kind`} value={newKind} onChange={(e) => setNewKind(e.target.value as FieldKind)} className={CONTROL}>
            {(Object.keys(KIND_LABEL) as FieldKind[]).map((k) => (
              <option key={k} value={k}>{KIND_LABEL[k]}</option>
            ))}
          </select>
        </div>
        <Button
          variant="secondary"
          disabled={!newPath.trim()}
          onClick={() => {
            onChange([...fields, { path: newPath.trim(), kind: newKind, value: newKind === 'boolean' ? 'false' : '' }])
            setNewPath('')
          }}
        >
          <Plus size={14} aria-hidden /> Adicionar
        </Button>
      </div>
    </fieldset>
  )
}
