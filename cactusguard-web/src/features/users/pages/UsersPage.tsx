import { useState } from 'react'
import { KeyRound, Pencil, Plus, UserCheck, UserX } from 'lucide-react'
import type { Role, UserAccount } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { DataTable } from '@/components/ui/DataTable'
import type { Column } from '@/components/ui/DataTable'
import { Card, PageHeader } from '@/components/ui/Layout'
import { useAuth } from '@/features/auth/useAuth'
import { useListState } from '@/hooks/useListState'
import { formatDate } from '@/lib/format'
import { ResetPasswordDialog } from '../components/ResetPasswordDialog'
import { UserFormDialog } from '../components/UserFormDialog'
import { ROLE_INFO, ROLE_ORDER } from '../roles'
import { useActivateUser, useDeactivateUser, useUsers } from '../hooks'

type Dialog = { kind: 'create' } | { kind: 'edit'; user: UserAccount } | { kind: 'deactivate'; user: UserAccount } | { kind: 'activate'; user: UserAccount } | { kind: 'reset'; user: UserAccount } | null

const NO_KEYS = [] as const

export function UsersPage() {
  const { can, user: me } = useAuth()
  const list = useListState(NO_KEYS, 'name,asc')
  const q = useUsers({ page: list.page, size: list.size, sort: list.sort })
  const deactivate = useDeactivateUser()
  const activate = useActivateUser()
  const [dialog, setDialog] = useState<Dialog>(null)
  const manage = can('users:manage')

  const columns: Column<UserAccount>[] = [
    { key: 'name', header: 'Nome', sortField: 'name', render: (u) => <span className="font-medium">{u.name}</span> },
    { key: 'username', header: 'Usuário', sortField: 'username', render: (u) => <span className="font-mono">{u.username}</span> },
    { key: 'email', header: 'E-mail', render: (u) => u.email },
    { key: 'roles', header: 'Perfis', render: (u) => <span className="inline-flex flex-wrap gap-1">{u.roles.map((r) => <Badge key={r} tone="info">{ROLE_INFO[r as Role]?.label ?? r}</Badge>)}</span> },
    { key: 'status', header: 'Situação', sortField: 'active', render: (u) => (u.active ? <Badge tone="success">Ativo</Badge> : <Badge tone="neutral">Inativo</Badge>) },
    { key: 'created', header: 'Criado em', sortField: 'createdAt', render: (u) => formatDate(u.createdAt) },
    {
      key: 'actions',
      header: 'Ações',
      render: (u) =>
        manage ? (
          <span className="flex gap-1">
            <Button variant="ghost" className="!px-2 !py-1 text-xs" onClick={() => setDialog({ kind: 'edit', user: u })} aria-label={`Editar ${u.name}`}><Pencil size={14} aria-hidden /> Editar</Button>
            <Button variant="ghost" className="!px-2 !py-1 text-xs" onClick={() => setDialog({ kind: 'reset', user: u })} aria-label={`Redefinir senha de ${u.name}`}><KeyRound size={14} aria-hidden /> Senha</Button>
            {u.active ? (
              <Button variant="ghost" className="!px-2 !py-1 text-xs" disabled={u.id === me?.id} title={u.id === me?.id ? 'Você não pode desativar a si mesmo' : undefined} onClick={() => setDialog({ kind: 'deactivate', user: u })} aria-label={`Desativar ${u.name}`}><UserX size={14} aria-hidden /> Desativar</Button>
            ) : (
              <Button variant="ghost" className="!px-2 !py-1 text-xs" onClick={() => setDialog({ kind: 'activate', user: u })} aria-label={`Reativar ${u.name}`}><UserCheck size={14} aria-hidden /> Reativar</Button>
            )}
          </span>
        ) : null,
    },
  ]

  return (
    <>
      <PageHeader
        title="Usuários e perfis"
        subtitle="Acesso restrito a administradores"
        actions={manage ? <Button onClick={() => setDialog({ kind: 'create' })}><Plus size={16} aria-hidden /> Novo usuário</Button> : undefined}
      />
      <Card title="Perfis de acesso" className="mb-4">
        <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {ROLE_ORDER.map((r) => (
            <div key={r}>
              <dt className="text-sm font-semibold">{ROLE_INFO[r].label} <span className="font-mono text-xs text-muted">({r})</span></dt>
              <dd className="text-sm text-muted">{ROLE_INFO[r].description}</dd>
            </div>
          ))}
        </dl>
      </Card>
      <DataTable
        caption="Lista de usuários"
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={() => void q.refetch()}
        rowKey={(u) => u.id}
        sort={list.sort}
        onSort={list.setSort}
        onPage={list.setPage}
        emptyTitle="Nenhum usuário encontrado"
      />
      {dialog?.kind === 'create' && <UserFormDialog onClose={() => setDialog(null)} />}
      {dialog?.kind === 'reset' && <ResetPasswordDialog user={dialog.user} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'edit' && <UserFormDialog user={dialog.user} onClose={() => setDialog(null)} />}
      <ConfirmDialog
        open={dialog?.kind === 'deactivate'}
        title="Desativar usuário"
        description={`${dialog?.kind === 'deactivate' ? dialog.user.name : ''} perderá o acesso ao sistema imediatamente. O histórico de ações é preservado.`}
        confirmLabel="Desativar"
        onCancel={() => setDialog(null)}
        onConfirm={async () => {
          if (dialog?.kind === 'deactivate') await deactivate.mutateAsync(dialog.user.id)
          setDialog(null)
        }}
      />
      <ConfirmDialog
        open={dialog?.kind === 'activate'}
        title="Reativar usuário"
        description={`${dialog?.kind === 'activate' ? dialog.user.name : ''} voltará a ter acesso conforme seus perfis.`}
        confirmLabel="Reativar"
        onCancel={() => setDialog(null)}
        onConfirm={async () => {
          if (dialog?.kind === 'activate') await activate.mutateAsync(dialog.user.id)
          setDialog(null)
        }}
      />
    </>
  )
}
