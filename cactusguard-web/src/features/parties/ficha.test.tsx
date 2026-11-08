import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { pld } from '@/mocks/pldData'
import { loginAs, renderApp } from '@/test/utils'
import { isReceitaIrregular, isRecentCompany } from './rules'
import { validateKycFile } from '@/features/kyc/upload'

describe('regras de apresentação da ficha', () => {
  it('identifica situações irregulares na Receita (com ou sem acento/caixa)', () => {
    for (const s of ['TITULAR_FALECIDO', 'suspensa', 'Cancelada', 'INAPTA', 'baixada', 'NULA', 'Titular falecido']) expect(isReceitaIrregular(s)).toBe(true)
    for (const s of ['REGULAR', 'ATIVA', null, undefined]) expect(isReceitaIrregular(s)).toBe(false)
  })
  it('destaca empresa aberta há menos de 1 ano', () => {
    const now = Date.parse('2026-11-08T00:00:00Z')
    expect(isRecentCompany('2026-06-01', now)).toBe(true)
    expect(isRecentCompany('2024-01-01', now)).toBe(false)
    expect(isRecentCompany(null, now)).toBe(false)
  })
  it('valida tipo e tamanho do upload de KYC', () => {
    expect(validateKycFile({ name: 'rg.pdf', size: 1000, type: 'application/pdf' })).toBeNull()
    expect(validateKycFile({ name: 'virus.exe', size: 1000, type: 'application/octet-stream' })).toMatch(/Tipo de arquivo/)
    expect(validateKycFile({ name: 'grande.pdf', size: 11 * 1024 * 1024, type: 'application/pdf' })).toMatch(/10 MB/)
  })
})

describe('ficha 360º (MSW)', () => {
  it('mostra alerta de titular falecido na Receita', async () => {
    loginAs('u-analyst')
    renderApp('/partes/p-0003')
    expect(await screen.findByRole('heading', { name: 'Antônio Carlos Medeiros' })).toBeInTheDocument()
    expect(await screen.findByText(/Situação irregular: Titular falecido/)).toBeInTheDocument()
  })

  it('mostra a faixa de bloqueio de bens CSNU', async () => {
    loginAs('u-analyst')
    renderApp('/partes/p-0004')
    const banner = await screen.findByText(/Bloqueio de bens ativo \(CSNU\)/)
    expect(banner.closest('[role="alert"]')).toBeInTheDocument()
    expect(screen.getByText(/Não comunicar ao cliente/)).toBeInTheDocument()
  })

  it('PEP ativo aparece no cabeçalho e na aba PEP; analista não vê o botão Aprovar', async () => {
    loginAs('u-analyst')
    renderApp('/partes/p-0001?aba=pep')
    expect(await screen.findByText('Secretário municipal de Obras')).toBeInTheDocument()
    expect(screen.getAllByText(/PEP ativo/).length).toBeGreaterThan(0)
    expect(screen.getByText('Pendente')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Aprovar relacionamento/ })).not.toBeInTheDocument()
    expect(screen.getByText(/Somente Supervisor pode aprovar/)).toBeInTheDocument()
  })

  it('auditor não vê Marcar PEP nem Aprovar', async () => {
    loginAs('u-auditor')
    renderApp('/partes/p-0001?aba=pep')
    await screen.findByText('Secretário municipal de Obras')
    expect(screen.queryByRole('button', { name: /Marcar|Atualizar PEP/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Aprovar relacionamento/ })).not.toBeInTheDocument()
  })

  it('supervisor aprova PEP com justificativa obrigatória', async () => {
    loginAs('u-super')
    renderApp('/partes/p-0001?aba=pep')
    await userEvent.click(await screen.findByRole('button', { name: /Aprovar relacionamento/ }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('checkbox'))
    await userEvent.type(within(dialog).getByLabelText(/justificativa/i), 'curta')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Aprovar' }))
    expect(await within(dialog).findByText('Informe ao menos 20 caracteres.')).toBeInTheDocument()
    expect(pld.pepApprovals).toHaveLength(0)

    await userEvent.clear(within(dialog).getByLabelText(/justificativa/i))
    await userEvent.type(within(dialog).getByLabelText(/justificativa/i), 'Aprovado pela alta administração após análise do dossiê.')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Aprovar' }))
    await waitFor(() => expect(pld.pepApprovals).toHaveLength(1))
    expect(pld.pepApprovals[0]).toMatchObject({ partyId: 'p-0001', by: 'u-super' })
    expect(await screen.findByText(/Aprovada em/)).toBeInTheDocument()
  })

  it('PJ sem UBO identificado mostra alerta', async () => {
    loginAs('u-analyst')
    renderApp('/partes/p-0002?aba=ubo')
    expect(await screen.findByText('Beneficiário final não identificado')).toBeInTheDocument()
    expect((await screen.findAllByText('Beatriz Nogueira')).length).toBeGreaterThan(0)
  })

  it('lista de partes pagina no servidor e filtra por PEP', async () => {
    loginAs('u-analyst')
    renderApp('/partes')
    expect(await screen.findByText(/Página 1 de 2/)).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('PEP'), 'true')
    await waitFor(() => expect(screen.getAllByText('PEP').length).toBeGreaterThan(1))
    expect(screen.queryByText('Antônio Carlos Medeiros')).not.toBeInTheDocument()
  })
})
