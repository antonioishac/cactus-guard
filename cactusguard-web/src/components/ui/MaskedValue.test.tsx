import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { db, PARTY_PF } from '@/mocks/data'
import { loginAs, renderWithQuery } from '@/test/utils'
import { MaskedValue } from './MaskedValue'

describe('MaskedValue', () => {
  it('mostra o valor mascarado como veio da API', () => {
    loginAs('u-analyst')
    renderWithQuery(<MaskedValue value="***.456.789-**" partyId={PARTY_PF} field="document" label="CPF" />)
    expect(screen.getByTestId('masked-value')).toHaveTextContent('***.456.789-**')
  })

  it('revelar pede motivo, chama o backend (registrando o acesso) e mostra o valor', async () => {
    loginAs('u-analyst')
    renderWithQuery(<MaskedValue value="***.456.789-**" partyId={PARTY_PF} field="document" label="CPF" />)

    await userEvent.click(screen.getByRole('button', { name: 'Revelar CPF' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText(/justificativa/i), 'Conferência cadastral do dossiê')
    await userEvent.click(within(dialog).getByRole('checkbox'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Revelar' }))

    await waitFor(() => expect(screen.getByTestId('masked-value')).toHaveTextContent('123.456.789-09'))
    expect(db.revealLog).toEqual([{ partyId: PARTY_PF, field: 'document', reason: 'Conferência cadastral do dossiê', by: 'u-analyst' }])
    // valor revelado nunca vai para storage
    expect(JSON.stringify({ ...sessionStorage, ...localStorage })).not.toContain('123.456.789-09')

    await userEvent.click(screen.getByRole('button', { name: 'Ocultar CPF' }))
    expect(screen.getByTestId('masked-value')).toHaveTextContent('***.456.789-**')
  })

  it('auditor não tem a ação de revelar', () => {
    loginAs('u-auditor')
    renderWithQuery(<MaskedValue value="***.456.789-**" partyId={PARTY_PF} field="document" label="CPF" />)
    expect(screen.queryByRole('button', { name: 'Revelar CPF' })).not.toBeInTheDocument()
  })
})
