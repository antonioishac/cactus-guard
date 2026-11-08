import { api } from './client'
import { pageQuery } from './params'
import type { AppNotification, Page, PageParams, UUID } from './types'

export async function listNotifications(unreadOnly: boolean, paging: PageParams): Promise<Page<AppNotification>> {
  return (await api.get<Page<AppNotification>>('/notifications', { params: { unreadOnly, ...pageQuery(paging) } })).data
}

export async function getUnreadCount(): Promise<number> {
  return (await api.get<{ count: number }>('/notifications/unread-count')).data.count
}

export async function markNotificationRead(id: UUID): Promise<void> {
  await api.post(`/notifications/${id}/read`)
}

/** Marca só as notificações do usuário autenticado. */
export async function markAllNotificationsRead(): Promise<number> {
  return (await api.post<{ updated: number }>('/notifications/read-all')).data.updated
}
