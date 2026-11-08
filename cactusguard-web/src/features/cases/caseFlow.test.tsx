import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { db } from '@/mocks/data'
import { loginAs, renderApp } from '@/test/utils'

const JUST = 'Movimentação em espécie incompatível com a renda declarada.'

async function fillAndConfirm(confirmName: string, text = JUST) {
  const dialog = await screen.findByRole('dialog')
  await userEvent.type(within(dialog).getByLabelText(/justificativa/i), text)
  await userEvent.click(within(dialog).getByRole('checkbox'))
  await userEvent.click(within(dialog).getByRole('button', { name: confirmName }))
}

describe('fluxo de decisão do case (MSW)', () => {
  it('analista decide COMUNICAR -> case vai para PENDING_APPROVAL e Aprovar não aparece para ele', async () => {
    loginAs('u-analyst')
    renderApp('/casos/c-0001')

    await userEvent.click(await screen.findByRole('button', { name: /Comunicar/ }))
    await fillAndConfirm('Registrar decisão: Comunicar')

    expect(await screen.findByText('Aguardando aprovação (quatro olhos)')).toBeInTheDocument()
    expect(db.cases.find((c) => c.id === 'c-0001')).toMatchObject({ status: 'PENDING_APPROVAL', decision: 'COMMUNICATE', decidedById: 'u-analyst' })
    expect(screen.queryByRole('button', { name: /Aprovar/ })).not.toBeInTheDocument()
    // botões de decisão desabilitados enquanto aguarda aprovação
    expect(screen.getByRole('button', { name: /Comunicar/ })).toBeDisabled()
  })

  it('justificativa curta é barrada no cliente e nada é enviado', async () => {
    loginAs('u-analyst')
    renderApp('/casos/c-0001')
    await userEvent.click(await screen.findByRole('button', { name: /Arquivar/ }))
    await fillAndConfirm('Registrar decisão: Arquivar', 'muito curta')
    expect(await screen.findByText('Informe ao menos 20 caracteres.')).toBeInTheDocument()
    expect(db.cases.find((c) => c.id === 'c-0001')?.status).toBe('OPEN')
  })

  it('erro do backend (422) aparece no modal', async () => {
    loginAs('u-analyst')
    db.cases.find((c) => c.id === 'c-0001')!.decidedAt = new Date().toISOString() // backend rejeita nova decisão
    renderApp('/casos/c-0001')
    await userEvent.click(await screen.findByRole('button', { name: /Arquivar/ }))
    await fillAndConfirm('Registrar decisão: Arquivar')
    expect(await within(await screen.findByRole('dialog')).findByRole('alert')).toHaveTextContent(/já foi decidido/)
  })

  it('supervisor diferente do decisor aprova -> case segue para comunicação com prazo', async () => {
    loginAs('u-super')
    renderApp('/casos/c-0002')
    expect(await screen.findByText('Aguardando aprovação (quatro olhos)')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /Aprovar decisão/ }))
    await fillAndConfirm('Aprovar', 'Concordo com a fundamentação do analista.')

    await waitFor(() => expect(db.cases.find((c) => c.id === 'c-0002')).toMatchObject({ status: 'COMMUNICATE', approvedById: 'u-super' }))
    await waitFor(() => expect(screen.queryByText('Aguardando aprovação (quatro olhos)')).not.toBeInTheDocument())
    expect(db.cases.find((c) => c.id === 'c-0002')?.communicationDeadline).toBeTruthy()
  })

  it('supervisor que propôs a decisão não vê Aprovar (quatro olhos)', async () => {
    loginAs('u-super')
    db.cases.find((c) => c.id === 'c-0002')!.decidedById = 'u-super'
    renderApp('/casos/c-0002')
    expect(await screen.findByText('Aguardando aprovação (quatro olhos)')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Aprovar decisão/ })).not.toBeInTheDocument()
    expect(screen.getByText(/Você propôs esta decisão/)).toBeInTheDocument()
  })

  it('analista não vê Aprovar; auditor não vê nenhuma ação (somente leitura)', async () => {
    loginAs('u-analyst')
    const a = renderApp('/casos/c-0002')
    expect(await screen.findByText('Aguardando aprovação (quatro olhos)')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Aprovar decisão/ })).not.toBeInTheDocument()
    a.unmount()

    const { useAuthStore } = await import('@/stores/authStore')
    useAuthStore.getState().clear()
    loginAs('u-auditor')
    renderApp('/casos/c-0001')
    expect(await screen.findByRole('heading', { name: /Dossiê/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Comunicar/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Arquivar/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Escalar/ })).not.toBeInTheDocument()
  })

  it('dossiê mostra os campos do catálogo 046 com PII mascarada', async () => {
    loginAs('u-analyst')
    renderApp('/casos/c-0002') // parte PF, PEP
    expect(await screen.findByText('***.456.789-**')).toBeInTheDocument()
    expect(await screen.findByText('M*** da S*** S***')).toBeInTheDocument() // nome da mãe mascarado
    expect(screen.getByText('REGULAR')).toBeInTheDocument() // situação na Receita
    expect(screen.getByText('Secretário municipal de Obras')).toBeInTheDocument() // PEP cargo
    expect(screen.getByText(/Carta 4.001: HIGH_VALUE/)).toBeInTheDocument()
  })
})
