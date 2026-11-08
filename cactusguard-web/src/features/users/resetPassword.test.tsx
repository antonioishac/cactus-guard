import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { pld } from '@/mocks/pldData'
import { loginAs, renderApp } from '@/test/utils'

describe('reset de senha (ADMIN)', () => {
  it('senha temporária é exibida UMA vez, com aviso e cópia, e some ao concluir', async () => {
    loginAs('u-admin')
    renderApp('/usuarios')
    await userEvent.click(await screen.findByRole('button', { name: 'Redefinir senha de Ana Martins' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: /Redefinir senha/ }))

    const shown = await within(dialog).findByLabelText('Senha temporária')
    expect(shown.textContent).toMatch(/^Tmp#/)
    expect(within(dialog).getByText(/exibida apenas uma vez/)).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Copiar senha temporária' })).toBeInTheDocument()
    expect(pld.passwordResets).toEqual([{ userId: 'u-analyst', generated: true }])

    const secret = shown.textContent ?? ''
    await userEvent.click(within(dialog).getByRole('button', { name: 'Concluir' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    // nunca persistida: nem no DOM, nem em storage
    expect(document.body.textContent).not.toContain(secret)
    expect(JSON.stringify({ ...sessionStorage, ...localStorage })).not.toContain(secret)
  })

  it('definir nova senha valida 10–128 e a senha definida nunca é devolvida', async () => {
    loginAs('u-admin')
    renderApp('/usuarios')
    await userEvent.click(await screen.findByRole('button', { name: 'Redefinir senha de Ana Martins' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('radio', { name: /Definir uma nova senha/ }))
    await userEvent.type(within(dialog).getByLabelText(/Nova senha/), 'curta')
    await userEvent.click(within(dialog).getByRole('button', { name: /Redefinir senha/ }))
    expect(await within(dialog).findByText('A senha deve ter entre 10 e 128 caracteres.')).toBeInTheDocument()
    expect(pld.passwordResets).toEqual([])

    await userEvent.clear(within(dialog).getByLabelText(/Nova senha/))
    await userEvent.type(within(dialog).getByLabelText(/Nova senha/), 'SenhaForte#2026')
    await userEvent.click(within(dialog).getByRole('button', { name: /Redefinir senha/ }))
    expect(await within(dialog).findByText(/Senha redefinida/)).toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Senha temporária')).not.toBeInTheDocument()
    expect(pld.passwordResets).toEqual([{ userId: 'u-analyst', generated: false }])
  })

  it('edição valida e-mail duplicado (422 EMAIL_IN_USE) no modal', async () => {
    loginAs('u-admin')
    renderApp('/usuarios')
    await userEvent.click(await screen.findByRole('button', { name: 'Editar Ana Martins' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.clear(within(dialog).getByLabelText('E-mail'))
    await userEvent.type(within(dialog).getByLabelText('E-mail'), 'supervisor@cactusguard.exemplo')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(/E-mail já em uso/)
  })

  it('reativar usuário inativo chama POST /users/{id}/activate', async () => {
    loginAs('u-admin')
    renderApp('/usuarios')
    await userEvent.click(await screen.findByRole('button', { name: 'Reativar Carlos Antigo' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Reativar' }))
    await waitFor(() => expect(pld.users.find((u) => u.id === 'u-old')?.active).toBe(true))
  })
})
