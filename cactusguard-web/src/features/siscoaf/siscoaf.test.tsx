import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { db2b, resetDb2b } from '@/mocks/handlers/db2b'
import { preload2B } from '@/test/preload2b'
import { loginAs, renderApp } from '@/test/utils'
import { siscoafDeadline } from './deadline'

const H = 3_600_000
const NOW = Date.parse('2026-11-08T12:00:00Z')
const at = (offsetMs: number) => new Date(NOW + offsetMs).toISOString()

beforeAll(() => preload2B(), 30_000)
beforeEach(() => resetDb2b())

describe('semáforo do prazo SISCOAF', () => {
  it('classifica no prazo, vencendo e vencido', () => {
    expect(siscoafDeadline({ status: 'DRAFT', deadlineAt: at(30 * H), submittedAt: null }, NOW).state).toBe('ok')
    expect(siscoafDeadline({ status: 'DRAFT', deadlineAt: at(6 * H), submittedAt: null }, NOW).state).toBe('warning')
    expect(siscoafDeadline({ status: 'APPROVED', deadlineAt: at(-H), submittedAt: null }, NOW).state).toBe('overdue')
  })
  it('enviada fica cumprida (e marca atraso); cancelada não tem prazo', () => {
    expect(siscoafDeadline({ status: 'SUBMITTED', deadlineAt: at(-5 * H), submittedAt: at(-6 * H) }, NOW)).toMatchObject({ state: 'done', late: false })
    expect(siscoafDeadline({ status: 'SUBMITTED', deadlineAt: at(-5 * H), submittedAt: at(-H) }, NOW)).toMatchObject({ state: 'done', late: true })
    expect(siscoafDeadline({ status: 'CANCELLED', deadlineAt: at(-5 * H), submittedAt: null }, NOW).state).toBe('none')
  })
})

async function fillAndConfirm(confirmName: string, text: string) {
  const dialog = await screen.findByRole('dialog')
  if (text) await userEvent.type(within(dialog).getByLabelText(/justificativa/i), text)
  await userEvent.click(within(dialog).getByRole('checkbox'))
  await userEvent.click(within(dialog).getByRole('button', { name: confirmName }))
}

describe('ações SISCOAF', () => {
  it('cancelar sem motivo é barrado; com motivo cancela', async () => {
    loginAs('u-super')
    renderApp('/siscoaf/s-0001-aaaa')
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar' }))
    await fillAndConfirm('Cancelar comunicação', '')
    expect(await screen.findByText('A justificativa é obrigatória.')).toBeInTheDocument()
    expect(db2b.siscoaf.find((c) => c.id === 's-0001-aaaa')?.status).toBe('DRAFT')

    await userEvent.type(within(screen.getByRole('dialog')).getByLabelText(/justificativa/i), 'Case reavaliado: operação compatível com o perfil.')
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar comunicação' }))
    await waitFor(() => expect(db2b.siscoaf.find((c) => c.id === 's-0001-aaaa')?.status).toBe('CANCELLED'))
  })

  it('AUDITOR vê o detalhe somente leitura, sem botões de ação', async () => {
    loginAs('u-auditor')
    renderApp('/siscoaf/s-0001-aaaa')
    expect(await screen.findByText(/somente leitura a esta comunicação/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enviar ao COAF' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancelar' })).not.toBeInTheDocument()
    expect(screen.getByText(/art\. 11, II/)).toBeInTheDocument()
  })

  it('declaração negativa bloqueada mostra o motivo do backend', async () => {
    loginAs('u-super')
    renderApp('/siscoaf/declaracao-negativa')
    await userEvent.selectOptions(await screen.findByLabelText('Ano de referência'), String(new Date().getFullYear()))
    // sem COS/COE enviada no ano corrente? o seed tem envios recentes; força um envio no ano
    db2b.siscoaf.find((c) => c.id === 's-0006-aaaa')!.submittedAt = new Date().toISOString()
    await userEvent.click(screen.getByRole('button', { name: 'Gerar declaração negativa' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Gerar' }))
    expect(await screen.findByText(/Declaracao negativa nao permitida/)).toBeInTheDocument()
  })
})
