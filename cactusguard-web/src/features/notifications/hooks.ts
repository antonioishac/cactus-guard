import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getUnreadCount, listNotifications, markNotificationRead } from '@/api/notifications'
import type { UUID } from '@/api/types'

export const NOTIFICATION_POLL_MS = 30_000

/** Contador de não lidas: polling leve (pausa com a aba em segundo plano). Falha silenciosa. */
export const useUnreadCount = () =>
  useQuery({ queryKey: ['notifications', 'count'], queryFn: getUnreadCount, refetchInterval: NOTIFICATION_POLL_MS, retry: false })

export const useNotifications = (enabled: boolean) =>
  useQuery({ queryKey: ['notifications', 'list'], queryFn: () => listNotifications(false, { page: 0, size: 10, sort: 'createdAt,desc' }), enabled, retry: false })

export const useMarkRead = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: UUID) => markNotificationRead(id),
    meta: { silent: true },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['notifications'] }),
  })
}
