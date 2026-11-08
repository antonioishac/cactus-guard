import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeAll, beforeEach, describe, expect, it } from 'vitest'
import type { RuleVersion } from '@/api/types'
import { diffObjects } from '@/lib/diff'
import { db2b, resetDb2b } from '@/mocks/handlers/db2b'
import { preload2B } from '@/test/preload2b'
import { loginAs, renderApp } from '@/test/utils'
import { fieldsToParams, paramsToFields, validateFields } from './params'

beforeAll(() => preload2B(), 30_000)
beforeEach(() => resetDb2b())

describe('diff de versões', () => {
  it('lista adicionados, removidos e alterados em parâmetros aninhados', () => {
    const v1: Pick<RuleVersion, 'snapshot'> = { snapshot: { severity: 'HIGH', parameters: { threshold_brl: 10000, window_days: 5, legacy: true } } }
    const v2: Pick<RuleVersion, 'snapshot'> = { snapshot: { severity: 'HIGH', parameters: { threshold_brl: 8000, window_days: 5, include_related: true } } }
    expect(diffObjects(v1.snapshot, v2.snapshot)).toEqual([
      { path: 'parameters.include_related', kind: 'added', after: true },
      { path: 'parameters.legacy', kind: 'removed', before: true },
      { path: 'parameters.threshold_brl', kind: 'changed', before: 10000, after: 8000 },
    ])
  })
})

describe('editor de parâmetros', () => {
  it('gera campos tipados, valida e reconstrói o JSON', () => {
    const fields = paramsToFields({ threshold_brl: 50000, per_operation: true, operations: ['DEPOSIT', 'WITHDRAWAL'], nested: { days: 7 } })
    expect(fields.map((f) => [f.path, f.kind])).toEqual([['threshold_brl', 'number'], ['per_operation', 'boolean'], ['operations', 'list'], ['nested.days', 'number']])
    expect(fieldsToParams(fields)).toEqual({ threshold_brl: 50000, per_operation: true, operations: ['DEPOSIT', 'WITHDRAWAL'], nested: { days: 7 } })
    const bad = fields.map((f) => (f.path === 'threshold_brl' ? { ...f, value: 'abc' } : f.path === 'nested.days' ? { ...f, value: '-1' } : f))
    expect(validateFields(bad)).toEqual({ threshold_brl: 'Informe um número válido.', 'nested.days': 'O valor não pode ser negativo.' })
  })
})

describe('aprovação de regra (quatro olhos)', () => {
  it('o criador não consegue aprovar: botão desabilitado com explicação', async () => {
    loginAs('u-super') // criou CASH_50K
    renderApp('/regras/CASH_50K')
    const btn = await screen.findByRole('button', { name: 'Aprovar regra' }, { timeout: 5000 })
    expect(btn).toBeDisabled()
    expect(screen.getByText(/outro supervisor precisa aprová-la/)).toBeInTheDocument()
  })

  it('outro supervisor aprova e a regra fica ativa', async () => {
    loginAs('u-super2')
    renderApp('/regras/CASH_50K')
    await userEvent.click(await screen.findByRole('button', { name: 'Aprovar regra' }, { timeout: 5000 }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText(/justificativa/i), 'Parâmetros conferidos com a Circular 3.978.')
    await userEvent.click(within(dialog).getByRole('checkbox'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Aprovar' }))
    await waitFor(() => expect(db2b.rules.find((r) => r.code === 'CASH_50K')?.status).toBe('ACTIVE'))
  })

  it('o backend também barra o criador (defesa em profundidade)', async () => {
    loginAs('u-analyst')
    renderApp('/regras/STRUCTURING')
    expect(await screen.findByText(/somente leitura às regras/)).toBeInTheDocument()
  })

  it('AUDITOR não vê botões de edição na lista nem no detalhe', async () => {
    loginAs('u-auditor')
    renderApp('/regras')
    expect(await screen.findByText('STRUCTURING')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nova regra' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Executar motor' })).not.toBeInTheDocument()
  })
})
