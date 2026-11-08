import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { db } from '@/mocks/data'
import { loginAs, renderApp } from '@/test/utils'

describe('fila de aprovação (GET /approvals/pending)', () => {
  it('lista cases, regras e PEPs; ownDecision/ownChange desabilitam a aprovação própria', async () => {
    loginAs('u-super')
    db.cases.find((c) => c.id === 'c-0002')!.decidedById = 'u-super' // ownDecision
    renderApp('/aprovacoes')
    expect(await screen.findByText('Você decidiu: outro supervisor aprova')).toBeInTheDocument()
    // regra CASH_50K foi criada pelo próprio supervisor (ownChange)
    expect(await screen.findByText('Você alterou: outro supervisor aprova')).toBeInTheDocument()
    // PEP sem aprovação da alta administração
    expect(await screen.findByRole('link', { name: /João da Silva Santos/ })).toBeInTheDocument()
    expect(screen.getByText(/PEPs sem aprovação da alta administração \(\d+\)/)).toBeInTheDocument()
  })

  it('outro supervisor vê o link para revisar e aprovar', async () => {
    loginAs('u-super2')
    renderApp('/aprovacoes')
    expect((await screen.findAllByRole('link', { name: 'Revisar e aprovar' })).length).toBe(2)
  })

  it('analista é barrado pela guarda de role', async () => {
    loginAs('u-analyst')
    renderApp('/aprovacoes')
    expect(await screen.findByText('Acesso negado')).toBeInTheDocument()
  })
})
