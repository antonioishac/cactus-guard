import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { db2b } from '@/mocks/handlers/db2b'
import { loginAs, renderApp } from '@/test/utils'

describe('notificações', () => {
  it('"marcar todas como lidas" usa POST /notifications/read-all e só afeta o usuário logado', async () => {
    loginAs('u-super')
    renderApp('/')
    await userEvent.click(await screen.findByRole('button', { name: /Notificações, 2 não lidas/ }))
    await userEvent.click(await screen.findByRole('button', { name: 'Marcar todas como lidas' }))
    await waitFor(() => expect(db2b.notifications['u-super']?.every((n) => n.read)).toBe(true))
    expect(db2b.notifications['u-analyst']?.every((n) => n.read)).toBe(false)
    expect(await screen.findByRole('button', { name: 'Notificações' })).toBeInTheDocument()
  })
})
