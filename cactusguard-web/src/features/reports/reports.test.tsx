import { QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { createQueryClient } from '@/app/queryClient'
import { generateReport } from '@/api/reports'
import { loginAs } from '@/test/utils'
import { useReportJob } from './hooks'
import { jobRefetchInterval } from './polling'


describe('polling de relatório', () => {
  it('só continua enquanto PENDING/PROCESSING', () => {
    expect(jobRefetchInterval({ status: 'PENDING' })).toBe(2000)
    expect(jobRefetchInterval({ status: 'PROCESSING' }, 50)).toBe(50)
    expect(jobRefetchInterval({ status: 'COMPLETED' })).toBe(false)
    expect(jobRefetchInterval({ status: 'FAILED' })).toBe(false)
    expect(jobRefetchInterval(undefined)).toBe(2000)
  })

  it('consulta o job até COMPLETED e então para', async () => {
    loginAs('u-analyst')
    const job = await generateReport('CASES_SLA', { format: 'PDF', parameters: { from: '2026-10-01', to: '2026-10-31' } })
    expect(job.status).toBe('PENDING')
    const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={createQueryClient()}>{children}</QueryClientProvider>
    const { result } = renderHook(() => useReportJob(job.id, 20), { wrapper })
    const seen = new Set<string>()
    await waitFor(
      () => {
        if (result.current.data) seen.add(result.current.data.status)
        expect(result.current.data?.status).toBe('COMPLETED')
      },
      { timeout: 4000 },
    )
    expect(result.current.data?.downloadable).toBe(true)
    const fetches = result.current.dataUpdatedAt
    await new Promise((r) => setTimeout(r, 120))
    expect(result.current.dataUpdatedAt).toBe(fetches) // parou de consultar
  })

  it('período inválido termina em FAILED', async () => {
    loginAs('u-analyst')
    const job = await generateReport('CASES_SLA', { format: 'EXCEL', parameters: { from: '1990-01-01', to: '1990-02-01' } })
    const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={createQueryClient()}>{children}</QueryClientProvider>
    const { result } = renderHook(() => useReportJob(job.id, 20), { wrapper })
    await waitFor(() => expect(result.current.data?.status).toBe('FAILED'), { timeout: 4000 })
    expect(result.current.data?.downloadable).toBe(false)
  })
})
