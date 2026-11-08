import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { Bell } from 'lucide-react'
import type { AppNotification } from '@/api/types'
import { formatDateTime } from '@/lib/format'
import { notificationLink } from './links'
import { useMarkRead, useNotifications, useUnreadCount } from './hooks'

export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const count = useUnreadCount()
  const list = useNotifications(open)
  const markRead = useMarkRead()
  const unread = count.data ?? 0

  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  function select(n: AppNotification) {
    if (!n.read) markRead.mutate(n.id)
    const to = notificationLink(n)
    if (to) {
      setOpen(false)
      void navigate(to)
    }
  }

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        aria-label={unread > 0 ? `Notificações, ${unread} não lidas` : 'Notificações'}
        aria-expanded={open}
        aria-haspopup="true"
        className="relative rounded-lg p-2 hover:bg-neutral-bg"
      >
        <Bell size={20} aria-hidden />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex min-w-5 items-center justify-center rounded-full bg-danger px-1 text-[11px] font-semibold text-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div role="region" aria-label="Notificações" className="absolute right-0 z-50 mt-2 w-[min(22rem,calc(100vw-2rem))] rounded-xl border border-line bg-surface shadow-xl">
          <p className="border-b border-line px-3 py-2 text-sm font-semibold">Notificações</p>
          <div className="max-h-96 overflow-y-auto">
            {list.isLoading && <p className="px-3 py-4 text-sm text-muted">Carregando…</p>}
            {list.error && <p className="px-3 py-4 text-sm text-muted">Não foi possível carregar as notificações.</p>}
            {list.data && list.data.content.length === 0 && <p className="px-3 py-4 text-sm text-muted">Nenhuma notificação.</p>}
            <ul className="divide-y divide-line">
              {list.data?.content.map((n) => (
                <li key={n.id}>
                  <button onClick={() => select(n)} className={`block w-full px-3 py-2 text-left text-sm hover:bg-canvas ${n.read ? '' : 'bg-info-bg/40'}`}>
                    <span className="flex items-center gap-2">
                      {!n.read && <span className="h-2 w-2 shrink-0 rounded-full bg-brand" aria-label="Não lida" />}
                      <span className="font-medium">{n.title}</span>
                    </span>
                    {n.message && <span className="block text-xs text-muted">{n.message}</span>}
                    <span className="block text-xs text-muted">{formatDateTime(n.createdAt)}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
    </div>
  )
}
