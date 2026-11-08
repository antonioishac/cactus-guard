import { useRef } from 'react'
import type { KeyboardEvent, ReactNode } from 'react'

export interface TabItem {
  id: string
  label: string
  /** destaque (ex.: alerta vermelho na aba) */
  alert?: boolean
}

/** Abas acessíveis (WAI-ARIA tabs): setas navegam, Home/End vão às pontas. O conteúdo é do chamador. */
export function Tabs({ tabs, active, onChange, label, children }: {
  tabs: TabItem[]
  active: string
  onChange: (id: string) => void
  label: string
  children: ReactNode
}) {
  const refs = useRef<Record<string, HTMLButtonElement | null>>({})

  function onKey(e: KeyboardEvent, index: number) {
    const last = tabs.length - 1
    const next = e.key === 'ArrowRight' ? (index === last ? 0 : index + 1) : e.key === 'ArrowLeft' ? (index === 0 ? last : index - 1) : e.key === 'Home' ? 0 : e.key === 'End' ? last : -1
    if (next < 0) return
    e.preventDefault()
    const id = tabs[next]!.id
    onChange(id)
    refs.current[id]?.focus()
  }

  return (
    <div>
      <div role="tablist" aria-label={label} className="mb-4 flex gap-1 overflow-x-auto border-b border-line">
        {tabs.map((t, i) => {
          const selected = t.id === active
          return (
            <button
              key={t.id}
              ref={(el) => {
                refs.current[t.id] = el
              }}
              role="tab"
              id={`tab-${t.id}`}
              aria-selected={selected}
              aria-controls={`panel-${t.id}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(t.id)}
              onKeyDown={(e) => onKey(e, i)}
              className={`-mb-px whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium ${selected ? 'border-brand text-brand' : 'border-transparent text-muted hover:text-ink'}`}
            >
              {t.label}
              {t.alert && <span className="ml-1 inline-block h-2 w-2 rounded-full bg-danger" aria-label="requer atenção" />}
            </button>
          )
        })}
      </div>
      <div role="tabpanel" id={`panel-${active}`} aria-labelledby={`tab-${active}`} tabIndex={0}>
        {children}
      </div>
    </div>
  )
}
