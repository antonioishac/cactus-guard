import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { db } from '@/mocks/data'
import { loginAs, renderApp } from '@/test/utils'

describe('alertas (contrato real)', () => {
  it('detalhe mostra evidências, transações, histórico e regra do backend', async () => {
    loginAs('u-analyst')
    renderApp('/alertas/a-0001')
    expect(await screen.findByText('Valor limite por transação')).toBeInTheDocument()
    expect(await screen.findByText('Transações que dispararam o alerta')).toBeInTheDocument()
    expect(screen.getByText(/Carta 4.001: Carta Circular 4.001\/2020/)).toBeInTheDocument()
    expect(screen.getByText('CREATED', { exact: false })).toBeInTheDocument()
  })

  it('comentário não muda o status e entra no histórico', async () => {
    loginAs('u-analyst')
    renderApp('/alertas/a-0001')
    await userEvent.type(await screen.findByLabelText('Novo comentário'), 'Aguardando retorno do gerente de contas.')
    await userEvent.click(screen.getByRole('button', { name: /Comentar/ }))
    await waitFor(() => expect(db.alertComments['a-0001']?.some((c) => c.text.startsWith('Aguardando retorno'))).toBe(true))
    expect(db.alerts.find((a) => a.id === 'a-0001')?.status).toBe('OPEN')
    expect(await screen.findByText('COMMENT', { exact: false })).toBeInTheDocument()
  })

  it('auditor lê comentários mas não vê o campo de escrita', async () => {
    loginAs('u-auditor')
    renderApp('/alertas/a-0001')
    expect(await screen.findByText(/Contato com a área comercial/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Novo comentário')).not.toBeInTheDocument()
  })

  it('abrir case exige justificativa e usa POST /cases', async () => {
    loginAs('u-analyst')
    renderApp('/alertas/a-0004') // a-0001 já tem case no seed
    await userEvent.click(await screen.findByRole('button', { name: /Abrir case/ }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText(/justificativa/i), 'curta')
    await userEvent.click(within(dialog).getByRole('checkbox'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Abrir case' }))
    expect(await within(dialog).findByText('Informe ao menos 20 caracteres.')).toBeInTheDocument()
    expect(db.cases.some((c) => c.alertId === 'a-0004')).toBe(false)

    await userEvent.clear(within(dialog).getByLabelText(/justificativa/i))
    await userEvent.type(within(dialog).getByLabelText(/justificativa/i), 'Seleção por incompatibilidade com o perfil.')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Abrir case' }))
    await waitFor(() => expect(db.alerts.find((a) => a.id === 'a-0004')?.history?.at(-1)).toMatchObject({ action: 'CASE_OPENED' }))
    expect(db.cases[0]).toMatchObject({ alertId: 'a-0004', status: 'OPEN' })
    expect(db.cases[0]!.caseNumber).toMatch(/^CG-\d{4}-\d{6}$/)
  })

  it('escalar alerta exige justificativa e grava ESCALATE no histórico', async () => {
    loginAs('u-analyst')
    renderApp('/alertas/a-0001')
    await userEvent.click(await screen.findByRole('button', { name: /Escalar/ }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText(/justificativa/i), 'Complexidade acima da alçada do analista.')
    await userEvent.click(within(dialog).getByRole('checkbox'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Escalar' }))
    await waitFor(() => expect(db.alerts.find((a) => a.id === 'a-0001')).toMatchObject({ status: 'ESCALATED' }))
    expect(db.alerts.find((a) => a.id === 'a-0001')?.history?.at(-1)).toMatchObject({ action: 'ESCALATE' })
  })

  it('lista filtra por regra e status no servidor', async () => {
    loginAs('u-analyst')
    renderApp('/alertas?ruleCode=STRUCTURING&status=OPEN')
    const rows = await screen.findAllByRole('link', { name: /STRUCTURING/ })
    expect(rows.length).toBeGreaterThan(0)
    expect(screen.queryByText(/HIGH_VALUE/)).not.toBeInTheDocument()
  })
})
