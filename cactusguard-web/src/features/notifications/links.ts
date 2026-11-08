import type { AppNotification } from '@/api/types'

/** Destino interno da notificação; null = sem link. Rotas de PLD apenas (nada voltado ao cliente). */
export function notificationLink(n: Pick<AppNotification, 'entityType' | 'entityId'>): string | null {
  if (!n.entityType || !n.entityId) return null
  switch (n.entityType) {
    case 'CASE': return `/casos/${n.entityId}`
    case 'ALERT': return `/alertas/${n.entityId}`
    case 'PARTY': return `/partes/${n.entityId}`
    case 'SISCOAF_COMMUNICATION': return `/siscoaf/${n.entityId}`
    case 'MONITORING_RULE': return '/regras'
    default: return null
  }
}
