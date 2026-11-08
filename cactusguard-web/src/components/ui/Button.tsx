import type { ButtonHTMLAttributes } from 'react'
import { Loader2 } from 'lucide-react'

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

const VARIANT: Record<Variant, string> = {
  primary: 'bg-brand text-white hover:bg-brand-hover',
  secondary: 'border border-line bg-surface text-ink hover:bg-neutral-bg',
  danger: 'bg-danger text-white hover:opacity-90',
  ghost: 'text-ink hover:bg-neutral-bg',
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  loading?: boolean
}

export function Button({ variant = 'primary', loading, disabled, className = '', children, type = 'button', ...rest }: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-3.5 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${VARIANT[variant]} ${className}`}
      {...rest}
    >
      {loading && <Loader2 size={14} className="animate-spin" aria-hidden />}
      {children}
    </button>
  )
}
