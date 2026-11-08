import type { ISODateTime, UUID } from './common'

/** Notification (o backend ainda serializa a entity; usar só estes campos). */
export interface AppNotification {
  id: UUID
  type: string
  title: string
  message: string | null
  read: boolean
  entityType: string | null
  entityId: UUID | null
  createdAt: ISODateTime
}
