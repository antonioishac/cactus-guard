import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { can } from '@/lib/permissions'
import { useAuthStore } from '@/stores/authStore'
import { loginAs, renderApp } from '@/test/utils'

describe('permissões por role (UI)', () => {
  it('Aprovar só SUPERVISOR; AUDITOR e ADMIN somente leitura', () => {
    expect(can(['SUPERVISOR'], 'case:approve')).toBe(true)
    expect(can(['ANALYST'], 'case:approve')).toBe(false)
    expect(can(['AUDITOR'], 'case:decide')).toBe(false)
    expect(can(['AUDITOR'], 'alert:assume')).toBe(false)
    expect(can(['ADMIN'], 'case:decide')).toBe(false)
    expect(can(['ANALYST'], 'case:decide')).toBe(true)
  })
})

describe('rotas protegidas', () => {
  it('sem sessão redireciona para /login', async () => {
    useAuthStore.getState().markAnonymous()
    renderApp('/alertas')
    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
  })

  it('ANALYST em rota de ADMIN vai para acesso negado', async () => {
    loginAs('u-analyst')
    renderApp('/usuarios')
    expect(await screen.findByText('Acesso negado')).toBeInTheDocument()
  })

  it('ADMIN acessa /usuarios; AUDITOR acessa /auditoria', async () => {
    loginAs('u-admin')
    const a = renderApp('/usuarios')
    expect(await screen.findByRole('heading', { name: 'Usuários e perfis' })).toBeInTheDocument()
    a.unmount()
    useAuthStore.getState().clear()
    loginAs('u-auditor')
    renderApp('/auditoria')
    expect(await screen.findByRole('heading', { name: 'Auditoria' })).toBeInTheDocument()
  })

  it('menu mostra só os módulos permitidos à role', async () => {
    loginAs('u-analyst')
    renderApp('/')
    const nav = await screen.findByRole('navigation', { name: 'Módulos' })
    expect(nav).toHaveTextContent('Alertas')
    expect(nav).not.toHaveTextContent('Usuários')
    expect(nav).not.toHaveTextContent('Auditoria')
  })
})

describe('tela de login', () => {
  it('valida campos obrigatórios', async () => {
    useAuthStore.getState().markAnonymous()
    renderApp('/login')
    await userEvent.click(await screen.findByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('Informe o usuário ou e-mail.')).toBeInTheDocument()
    expect(screen.getByText('Informe a senha.')).toBeInTheDocument()
  })

  it('credenciais inválidas mostram mensagem clara', async () => {
    useAuthStore.getState().markAnonymous()
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('Usuário ou e-mail'), 'analista')
    await userEvent.type(screen.getByLabelText('Senha'), 'errada')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('Credenciais inválidas.')).toBeInTheDocument()
  })

  it('login válido guarda sessão (refresh em sessionStorage) e entra no dashboard', async () => {
    useAuthStore.getState().markAnonymous()
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('Usuário ou e-mail'), 'analista')
    await userEvent.type(screen.getByLabelText('Senha'), 'senha123')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(useAuthStore.getState().user?.roles).toEqual(['ANALYST'])
    await waitFor(() => expect(sessionStorage.getItem('cg.rt')).toMatch(/^ref\./))
    expect(localStorage.length).toBe(0)
  })
})
