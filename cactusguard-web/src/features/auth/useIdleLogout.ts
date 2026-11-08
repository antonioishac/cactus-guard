import { useEffect } from 'react'

const EVENTS = ['mousedown', 'keydown', 'touchstart', 'scroll', 'mousemove'] as const

/**
 * Chama `onIdle` após `minutes` sem interação. Reinício com throttle de 1 s para não custar nada em mousemove.
 */
export function useIdleLogout(minutes: number, onIdle: () => void) {
  useEffect(() => {
    if (minutes <= 0) return
    const ms = minutes * 60_000
    let timer = setTimeout(onIdle, ms)
    let last = Date.now()
    function reset() {
      const now = Date.now()
      if (now - last < 1000) return
      last = now
      clearTimeout(timer)
      timer = setTimeout(onIdle, ms)
    }
    EVENTS.forEach((e) => window.addEventListener(e, reset, { passive: true }))
    return () => {
      clearTimeout(timer)
      EVENTS.forEach((e) => window.removeEventListener(e, reset))
    }
  }, [minutes, onIdle])
}
