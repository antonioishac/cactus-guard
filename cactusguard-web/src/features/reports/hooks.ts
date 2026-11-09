import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { generateReport, getReportJob, listMyReportJobs, listReportDefinitions } from '@/api/reports'
import type { PageParams, ReportGenerateRequest, ReportJob, UUID } from '@/api/types'
import { jobRefetchInterval, REPORT_POLL_MS } from './polling'

export const useReportDefinitions = () => useQuery({ queryKey: ['reports', 'definitions'], queryFn: listReportDefinitions })

/** Polling do job até COMPLETED/FAILED. */
export const useReportJob = (id: UUID | undefined, intervalMs = REPORT_POLL_MS) =>
  useQuery({
    queryKey: ['reports', 'job', id],
    queryFn: () => getReportJob(id!),
    enabled: !!id,
    refetchInterval: (q) => jobRefetchInterval(q.state.data, intervalMs),
    staleTime: 0,
  })

/** "Meus relatórios": faz polling enquanto houver job em andamento na página. */
export const useMyReports = (paging: PageParams, intervalMs = REPORT_POLL_MS) =>
  useQuery({
    queryKey: ['reports', 'mine', paging],
    queryFn: () => listMyReportJobs(paging),
    placeholderData: keepPreviousData,
    refetchInterval: (q) => (q.state.data?.content.some((j: ReportJob) => jobRefetchInterval(j) !== false) ? intervalMs : false),
  })

export const useGenerateReport = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { code: string; body: ReportGenerateRequest }) => generateReport(v.code, v.body),
    meta: { successMessage: 'Relatório na fila de geração.' },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['reports', 'mine'] }),
  })
}
