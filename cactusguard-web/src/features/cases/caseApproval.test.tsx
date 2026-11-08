import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { db } from '@/mocks/data'
import { db2b } from '@/mocks/handlers/db2b'
import { loginAs, renderApp } from '@/test/utils'

async function fillAndConfirm(confirmName: string, text: string) {
  const dialog = await screen.findByRole('dialog')
  await userEvent.type(within(dialog).getByLabelText(/justificativa/i), text)
  await userEvent.click(within(dialog).getByRole('checkbox'))
  await userEvent.click(within(dialog).getByRole('button', { name: confirmName }))
}

describe('rejeição da aprovação (quatro olhos)', () => {
  it('supervisor diferente do decisor rejeita: case volta a IN_ANALYSIS, decisão limpa, decisor notificado', async () => {
    loginAs('u-super')
    renderApp('/casos/c-0002')
    await userEvent.click(await screen.findByRole('button', { name: /Rejeitar aprovação/ }))
    await fillAndConfirm('Rejeitar aprovação', 'Fundamentação insuficiente: faltam extratos do período.')

    await waitFor(() => expect(db.cases.find((c) => c.id === 'c-0002')).toMatchObject({ status: 'IN_ANALYSIS', decision: null, decidedById: null, decidedAt: null }))
    // a decisão rejeitada e a justificativa ficam na trilha (append-only)
    expect(db.caseTrail['c-0002']?.at(-1)).toMatchObject({
      action: 'CASE_APPROVAL_REJECTED',
      details: { decision: 'COMMUNICATE', justification: 'Fundamentação insuficiente: faltam extratos do período.' },
    })
    expect(db2b.notifications['u-analyst']?.[0]).toMatchObject({ type: 'CASE_APPROVAL_REJECTED', entityId: 'c-0002' })
    // volta a aceitar nova decisão
    await waitFor(() => expect(screen.getByRole('button', { name: /Comunicar/ })).toBeEnabled())
  })

  it('rejeitar exige justificativa (20+ não brancos)', async () => {
    loginAs('u-super')
    renderApp('/casos/c-0002')
    await userEvent.click(await screen.findByRole('button', { name: /Rejeitar aprovação/ }))
    await fillAndConfirm('Rejeitar aprovação', 'curta')
    expect(await screen.findByText('Informe ao menos 20 caracteres.')).toBeInTheDocument()
    expect(db.cases.find((c) => c.id === 'c-0002')?.status).toBe('PENDING_APPROVAL')
  })

  it('quem decidiu não vê Rejeitar; analista também não', async () => {
    loginAs('u-super')
    db.cases.find((c) => c.id === 'c-0002')!.decidedById = 'u-super'
    const a = renderApp('/casos/c-0002')
    expect(await screen.findByText('Aguardando aprovação (quatro olhos)')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Rejeitar aprovação/ })).not.toBeInTheDocument()
    a.unmount()
    loginAs('u-analyst')
    renderApp('/casos/c-0002')
    expect(await screen.findByText('Aguardando aprovação (quatro olhos)')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Rejeitar aprovação/ })).not.toBeInTheDocument()
  })
})

describe('dossiê e SLA vindos do backend', () => {
  it('lista de cases mostra o slaStatus calculado pelo backend (sem cálculo no front)', async () => {
    loginAs('u-analyst')
    db.cases.find((c) => c.id === 'c-0001')!.analysisDeadline = new Date(Date.now() - 86_400_000).toISOString()
    renderApp('/casos?slaStatus=OVERDUE')
    expect(await screen.findByText(db.cases[0]!.caseNumber!)).toBeInTheDocument()
    const table = within(screen.getByRole('table'))
    expect(table.getAllByText('Vencido').length).toBeGreaterThan(0)
    expect(table.queryByText('No prazo')).not.toBeInTheDocument() // filtro slaStatus aplicado no servidor
  })

  it('dossiê real: evidências do alerta, transações e trilha do case', async () => {
    loginAs('u-analyst')
    renderApp('/casos/c-0002')
    expect(await screen.findByText('Valor limite por transação')).toBeInTheDocument()
    expect(await screen.findByText('CASE_DECIDED')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Do período' })).toBeInTheDocument()
  })

  it('nota é adicionada e case encerrado não oferece escrita', async () => {
    loginAs('u-analyst')
    const a = renderApp('/casos/c-0001')
    await userEvent.type(await screen.findByLabelText('Nova nota'), 'Contato feito com a área comercial.')
    await userEvent.click(screen.getByRole('button', { name: /Adicionar/ }))
    await waitFor(() => expect(db.notes['c-0001']?.some((n) => n.text.startsWith('Contato feito'))).toBe(true))
    a.unmount()
    renderApp('/casos/c-0003') // ARCHIVED
    expect(await screen.findByRole('heading', { name: /Dossiê/ })).toBeInTheDocument()
    expect(screen.queryByLabelText('Nova nota')).not.toBeInTheDocument()
  })

  it('anexar evidência envia multipart e rejeita tipo não permitido (422 do backend)', async () => {
    loginAs('u-analyst')
    renderApp('/casos/c-0001')
    const input = await screen.findByLabelText('Selecionar arquivo de evidência')
    await userEvent.upload(input, new File(['x'], 'planilha.exe', { type: 'application/octet-stream' }), { applyAccept: false })
    expect(await screen.findByText(/Tipo de arquivo não permitido/)).toBeInTheDocument()
    await userEvent.upload(input, new File(['%PDF'], 'extrato.pdf', { type: 'application/pdf' }))
    await waitFor(() => expect(db.evidences['c-0001']?.some((e) => e.fileName === 'extrato.pdf')).toBe(true))
  })
})
