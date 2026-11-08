import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { db2b } from '@/mocks/handlers/db2b'
import { loginAs, renderApp } from '@/test/utils'

async function fillAndConfirm(confirmName: string, text: string) {
  const dialog = await screen.findByRole('dialog')
  await userEvent.type(within(dialog).getByLabelText(/justificativa/i), text)
  await userEvent.click(within(dialog).getByRole('checkbox'))
  await userEvent.click(within(dialog).getByRole('button', { name: confirmName }))
}

describe('SISCOAF (contrato real)', () => {
  it('sem filtro lista todos os status (não assume DRAFT)', async () => {
    loginAs('u-super')
    renderApp('/siscoaf')
    // o seed tem rascunho, enviada e cancelada ao mesmo tempo
    expect((await screen.findAllByText('Rascunho')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Enviada').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Cancelada').length).toBeGreaterThan(0)
  })

  it('filtra por tipo no servidor', async () => {
    loginAs('u-super')
    renderApp('/siscoaf?type=COE')
    expect((await screen.findAllByText('Automática')).length).toBeGreaterThan(0)
    const table = within(screen.getByRole('table'))
    expect(table.queryByText('COS - operação suspeita')).not.toBeInTheDocument()
  })

  it('detalhe mostra o histórico de status do backend (siscoaf_history) com motivo e autor', async () => {
    loginAs('u-super')
    renderApp('/siscoaf/s-0005-aaaa')
    expect(await screen.findByText('Histórico de status')).toBeInTheDocument()
    expect(await screen.findByText(/Fundamentos conferidos no dossiê do case/)).toBeInTheDocument()
    expect(screen.getByText(/Sônia Prado/)).toBeInTheDocument()
  })

  it('quatro olhos exigido: DRAFT não pode ser enviado antes de aprovado; aprovar libera o envio', async () => {
    loginAs('u-super')
    renderApp('/siscoaf/s-0010-aaaa')
    expect(await screen.findByText(/Quatro olhos exigido/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enviar ao COAF' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Aprovar' }))
    await fillAndConfirm('Aprovar', 'Dossiê conferido; fundamentos suficientes para comunicar.')
    await waitFor(() => expect(db2b.siscoaf.find((c) => c.id === 's-0010-aaaa')).toMatchObject({ status: 'APPROVED', approvedById: 'u-super' }))
    expect(await screen.findByRole('button', { name: 'Enviar ao COAF' })).toBeInTheDocument()
  })

  it('enviar grava a justificativa no histórico e gera protocolo', async () => {
    loginAs('u-super')
    renderApp('/siscoaf/s-0005-aaaa')
    await userEvent.click(await screen.findByRole('button', { name: 'Enviar ao COAF' }))
    await fillAndConfirm('Enviar ao COAF', 'Envio no prazo regulatório D+1 útil.')
    await waitFor(() => expect(db2b.siscoaf.find((c) => c.id === 's-0005-aaaa')?.status).toBe('SUBMITTED'))
    const c = db2b.siscoaf.find((x) => x.id === 's-0005-aaaa')!
    expect(c.protocolNumber).toMatch(/^COAF-/)
    expect(c.statusHistory?.at(-1)).toMatchObject({ action: 'SUBMITTED', reason: 'Envio no prazo regulatório D+1 útil.' })
  })
})
