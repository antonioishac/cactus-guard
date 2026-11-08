import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { loginAs, renderApp } from '@/test/utils'


describe('auditoria', () => {
  it('ANALYST não acessa a rota (guarda por role)', async () => {
    loginAs('u-analyst')
    renderApp('/auditoria')
    expect(await screen.findByText('Acesso negado')).toBeInTheDocument()
  })

  it('AUDITOR consulta a trilha por entidade e expande o diff old/new', async () => {
    loginAs('u-auditor')
    renderApp('/auditoria?entityType=MONITORING_RULE&entityId=r-struct')
    expect(await screen.findByText('RULE_UPDATED')).toBeInTheDocument()
    await userEvent.click(screen.getAllByRole('button', { name: /Ver alterações/ })[0]!)
    expect(await screen.findByText('parameters.threshold_brl')).toBeInTheDocument()
  })

  it('log de acesso a dados pessoais por entidade', async () => {
    loginAs('u-auditor')
    renderApp('/auditoria?tab=access&entityType=PARTY&entityId=p-0001')
    expect(await screen.findByText('document, motherName')).toBeInTheDocument()
  })
})
