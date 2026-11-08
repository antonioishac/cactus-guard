import type { ISODateTime, UUID } from './common'

/** Entity Notification serializada. */
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
