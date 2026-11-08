import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { loginAs, renderApp } from '@/test/utils'

describe('auditoria global (GET /audit, GET /data-access)', () => {
  it('lista sem exigir entidade e filtra por login no servidor', async () => {
    loginAs('u-auditor')
    renderApp('/auditoria?username=analista')
    expect(await screen.findByText('SISCOAF_DRAFT_CREATED')).toBeInTheDocument()
    expect(screen.getByText('PARTY_UPDATED')).toBeInTheDocument()
    expect(screen.queryByText('RULE_CREATED')).not.toBeInTheDocument() // feita pelo supervisor
  })

  it('filtra por ação e período', async () => {
    loginAs('u-auditor')
    const from = new Date(Date.now() - 30 * 86_400_000).toISOString().slice(0, 10)
    renderApp(`/auditoria?action=RULE_UPDATED&from=${from}`)
    expect(await screen.findByText('RULE_UPDATED')).toBeInTheDocument()
    expect(screen.queryByText('SISCOAF_DRAFT_CREATED')).not.toBeInTheDocument()
  })

  it('log LGPD global com o motivo da revelação', async () => {
    loginAs('u-auditor')
    renderApp('/auditoria?tab=access')
    expect(await screen.findByText(/REVELACAO: análise do case/)).toBeInTheDocument()
    expect(screen.getByText(/REVELACAO: contato para documentação KYC/)).toBeInTheDocument()
  })

  it('log LGPD filtrado por usuário', async () => {
    loginAs('u-auditor')
    renderApp('/auditoria?tab=access&userId=u-super')
    expect(await screen.findByText(/REVELACAO: revisão de aprovação/)).toBeInTheDocument()
    expect(screen.queryByText(/contato para documentação KYC/)).not.toBeInTheDocument()
  })
})
