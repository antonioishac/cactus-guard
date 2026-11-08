/** Símbolo: três formas arredondadas (bumerangues) que se encontram, sem círculo central (specs 035/043). */
export function Logo({ className = 'h-8 w-8' }: { className?: string }) {
  return (
    <svg viewBox="0 0 48 48" className={className} role="img" aria-label="CactusGuard" fill="none" strokeWidth="7" strokeLinecap="round">
      <path d="M24 8 C12 8 8 20 14 30" className="stroke-accent" />
      <path d="M40 30 C40 18 32 12 24 8" className="stroke-gold" />
      <path d="M14 38 C22 44 36 42 40 30" className="stroke-neutral" />
    </svg>
  )
}
