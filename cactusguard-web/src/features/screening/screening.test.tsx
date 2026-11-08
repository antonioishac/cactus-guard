import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { pld } from '@/mocks/pldData'
import { loginAs, renderApp } from '@/test/utils'

async function openReview(name: RegExp) {
  await userEvent.click(await screen.findByRole('button', { name }))
  return screen.findByRole('dialog')
}

describe('screening (MSW)', () => {
  it('revisão de hit exige justificativa (>= 20) e confirmação', async () => {
    loginAs('u-analyst')
    renderApp('/screening')
    const dialog = await openReview(/Marcar hit Marcos Ribeiro Filho como falso positivo/)

    await userEvent.click(within(dialog).getByRole('button', { name: 'Marcar falso positivo' }))
    expect(await within(dialog).findByText('A justificativa é obrigatória.')).toBeInTheDocument()
    expect(within(dialog).getByText('Confirme para prosseguir.')).toBeInTheDocument()

    await userEvent.type(within(dialog).getByLabelText(/justificativa/i), 'muito curta')
    await userEvent.click(within(dialog).getByRole('checkbox'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Marcar falso positivo' }))
    expect(await within(dialog).findByText('Informe ao menos 20 caracteres.')).toBeInTheDocument()
    expect(pld.reviews).toHaveLength(0)

    await userEvent.clear(within(dialog).getByLabelText(/justificativa/i))
    await userEvent.type(within(dialog).getByLabelText(/justificativa/i), 'Homônimo: data de nascimento e nome da mãe divergem.')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Marcar falso positivo' }))
    await waitFor(() => expect(pld.reviews).toHaveLength(1))
    expect(pld.reviews[0]).toMatchObject({ id: 's-4', status: 'FALSE_POSITIVE' })
    await waitFor(() => expect(screen.queryByText('Marcos Ribeiro Filho')).not.toBeInTheDocument())
  })

  it('confirmar hit CSNU aplica o bloqueio de bens na parte', async () => {
    loginAs('u-super')
    renderApp('/screening')
    const dialog = await openReview(/Confirmar hit Viktor Petrov/)
    await userEvent.type(within(dialog).getByLabelText(/justificativa/i), 'Nome, data de nascimento e nacionalidade conferem com a lista CSNU.')
    await userEvent.click(within(dialog).getByRole('checkbox'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar hit' }))
    await waitFor(() => expect(pld.parties.find((p) => p.id === 'p-0004')?.assetFreezeActive).toBe(true))
  })

  it('auditor vê a fila sem botões de revisão', async () => {
    loginAs('u-auditor')
    renderApp('/screening')
    await screen.findByText('Marcos Ribeiro Filho')
    expect(screen.queryByRole('button', { name: /Confirmar hit/ })).not.toBeInTheDocument()
  })

  it('watchlist: somente supervisor inclui', async () => {
    loginAs('u-analyst')
    renderApp('/screening?aba=interna')
    // analista é barrado pela API (403): a seção avisa
    expect(await screen.findByText(/não tem permissão para ver esta seção/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Incluir/ })).not.toBeInTheDocument()
  })

  it('watchlist: supervisor vê o botão de incluir', async () => {
    loginAs('u-super')
    renderApp('/screening?aba=interna')
    expect(await screen.findByText('Rubens Tavares Neto')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Incluir/ })).toBeInTheDocument()
  })
})
