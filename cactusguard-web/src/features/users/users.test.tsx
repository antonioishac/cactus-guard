import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { pld } from '@/mocks/pldData'
import { loginAs, renderApp } from '@/test/utils'

describe('usuários e perfis (MSW)', () => {
  it.each(['u-analyst', 'u-super', 'u-auditor'])('%s é redirecionado para acesso negado', async (id) => {
    loginAs(id)
    renderApp('/usuarios')
    expect(await screen.findByRole('heading', { name: 'Acesso negado' })).toBeInTheDocument()
  })

  it('admin lista usuários e as descrições de perfil', async () => {
    loginAs('u-admin')
    renderApp('/usuarios')
    expect(await screen.findByText('Sérgio Lima')).toBeInTheDocument()
    expect(screen.getByText(/Somente leitura, incluindo trilha de auditoria/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Novo usuário' })).toBeInTheDocument()
  })

  it('admin cria usuário com validação de senha e perfil', async () => {
    loginAs('u-admin')
    renderApp('/usuarios')
    await userEvent.click(await screen.findByRole('button', { name: 'Novo usuário' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Nome'), 'Paulo Teixeira')
    await userEvent.type(within(dialog).getByLabelText('Usuário (login)'), 'paulo.t')
    await userEvent.type(within(dialog).getByLabelText('E-mail'), 'paulo@cactusguard.exemplo')
    await userEvent.type(within(dialog).getByLabelText(/Senha inicial/), 'curta')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Criar usuário' }))
    expect(await within(dialog).findByText('A senha deve ter entre 10 e 128 caracteres.')).toBeInTheDocument()
    expect(within(dialog).getByText('Selecione ao menos um perfil.')).toBeInTheDocument()

    await userEvent.clear(within(dialog).getByLabelText(/Senha inicial/))
    await userEvent.type(within(dialog).getByLabelText(/Senha inicial/), 'SenhaForte#2026')
    await userEvent.click(within(dialog).getByRole('checkbox', { name: /Analista/ }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Criar usuário' }))
    await waitFor(() => expect(pld.users.some((u) => u.username === 'paulo.t' && u.roles.includes('ANALYST'))).toBe(true))
  })

  it('admin desativa usuário com confirmação, mas não a si mesmo', async () => {
    loginAs('u-admin')
    renderApp('/usuarios')
    expect(await screen.findByRole('button', { name: 'Desativar Adriana Costa' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Desativar Ana Martins' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Desativar' }))
    await waitFor(() => expect(pld.users.find((u) => u.id === 'u-analyst')?.active).toBe(false))
  })
})
