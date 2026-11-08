import { useId } from 'react'
import type { ReactNode } from 'react'
import { Button } from './Button'

const CONTROL = 'w-full rounded-lg border border-line bg-surface px-2.5 py-1.5 text-sm'

export function SelectFilter({ label, value, onChange, options }: {
  label: string
  value: string
  onChange: (v: string) => void
  options: { value: string; label: string }[]
}) {
  const id = useId()
  return (
    <div className="min-w-36 flex-1">
      <label htmlFor={id} className="mb-1 block text-xs font-medium text-muted">{label}</label>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)} className={CONTROL}>
        <option value="">Todos</option>
        {options.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
    </div>
  )
}

export function TextFilter({ label, value, onChange, type = 'text', placeholder }: {
  label: string
  value: string
  onChange: (v: string) => void
  type?: 'text' | 'date'
  placeholder?: string
}) {
  const id = useId()
  return (
    <div className="min-w-36 flex-1">
      <label htmlFor={id} className="mb-1 block text-xs font-medium text-muted">{label}</label>
      <input id={id} type={type} value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} className={CONTROL} />
    </div>
  )
}

export function FilterBar({ children, onClear, hasActive }: { children: ReactNode; onClear: () => void; hasActive: boolean }) {
  return (
    <form role="search" aria-label="Filtros" onSubmit={(e) => e.preventDefault()} className="mb-4 flex flex-wrap items-end gap-3 rounded-xl border border-line bg-surface p-3">
      {children}
      <Button variant="ghost" onClick={onClear} disabled={!hasActive}>Limpar filtros</Button>
    </form>
  )
}
