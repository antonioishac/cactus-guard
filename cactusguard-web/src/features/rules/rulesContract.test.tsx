import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { loginAs, renderApp } from '@/test/utils'

describe('regras (contrato real)', () => {
  it('lista paginada no servidor inclui pendentes e inativas e mostra métricas de 30 dias', async () => {
    loginAs('u-super')
    renderApp('/regras')
    expect(await screen.findByText('CASH_50K')).toBeInTheDocument() // PENDING_APPROVAL
    expect(screen.getByText('TXN_VELOCITY')).toBeInTheDocument() // INACTIVE
    expect(screen.getByText('148')).toBeInTheDocument() // alertsGenerated
    expect(screen.getByText('34%')).toBeInTheDocument() // falsePositiveRate
  })

  it('filtro de status é aplicado no servidor', async () => {
    loginAs('u-super')
    renderApp('/regras?status=PENDING_APPROVAL')
    expect(await screen.findByText('CASH_50K')).toBeInTheDocument()
    expect(screen.queryByText('STRUCTURING')).not.toBeInTheDocument()
  })

  it('detalhe usa GET /rules/{code} e GET /rules/{code}/metrics', async () => {
    loginAs('u-auditor')
    renderApp('/regras/STRUCTURING')
    expect(await screen.findByText('Métricas (últimos 30 dias)')).toBeInTheDocument()
    expect(await screen.findByText('Falsos positivos')).toBeInTheDocument()
    expect(screen.getByText('Taxa de falso positivo')).toBeInTheDocument()
    expect(screen.getAllByText(/Aprovada por/).length).toBeGreaterThan(0)
  })

  it('regra inexistente mostra o 404 do contrato de erro (com ref.)', async () => {
    loginAs('u-super')
    renderApp('/regras/NAO_EXISTE')
    expect(await screen.findByText(/Regra não encontrada: NAO_EXISTE/)).toBeInTheDocument()
    expect(await screen.findByText(/Ref\.: mock-trace-/)).toBeInTheDocument()
  })
})
