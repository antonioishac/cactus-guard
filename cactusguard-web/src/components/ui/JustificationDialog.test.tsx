import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { JustificationDialog } from './JustificationDialog'

function setup(onConfirm = vi.fn().mockResolvedValue(undefined), extra: { reasonLabel?: string } = {}) {
  render(
    <JustificationDialog open title="Decidir" confirmLabel="Confirmar" onCancel={() => {}} onConfirm={onConfirm} {...extra} />,
  )
  return onConfirm
}

const LONG = 'Operações incompatíveis com a renda declarada pelo cliente.'

describe('JustificationDialog', () => {
  it('exige justificativa', async () => {
    const onConfirm = setup()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))
    expect(await screen.findByText('A justificativa é obrigatória.')).toBeInTheDocument()
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('rejeita justificativa menor que 20 caracteres', async () => {
    const onConfirm = setup()
    await userEvent.type(screen.getByLabelText(/justificativa/i), 'curta demais')
    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))
    expect(await screen.findByText('Informe ao menos 20 caracteres.')).toBeInTheDocument()
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('exige confirmação explícita (checkbox)', async () => {
    const onConfirm = setup()
    await userEvent.type(screen.getByLabelText(/justificativa/i), LONG)
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))
    expect(await screen.findByText('Confirme para prosseguir.')).toBeInTheDocument()
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('envia justificativa válida (sem espaços nas pontas)', async () => {
    const onConfirm = setup()
    await userEvent.type(screen.getByLabelText(/justificativa/i), `  ${LONG}  `)
    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith({ justification: LONG, reason: '' }))
  })

  it('campo de motivo é obrigatório quando solicitado', async () => {
    const onConfirm = setup(undefined, { reasonLabel: 'Motivo' })
    await userEvent.type(screen.getByLabelText(/justificativa/i), LONG)
    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))
    expect(await screen.findByText('O motivo é obrigatório.')).toBeInTheDocument()
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('mostra o erro do backend dentro do modal e não fecha', async () => {
    const onConfirm = vi.fn().mockRejectedValue(new Error('falha'))
    setup(onConfirm)
    await userEvent.type(screen.getByLabelText(/justificativa/i), LONG)
    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/Erro inesperado/)
  })
})
