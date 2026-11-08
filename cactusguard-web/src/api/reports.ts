import { api } from './client'
import { pageQuery } from './params'
import type { Page, PageParams, ReportDefinition, ReportGenerateRequest, ReportJob, UUID } from './types'

export async function listReportDefinitions(): Promise<ReportDefinition[]> {
  return (await api.get<ReportDefinition[]>('/reports')).data
}

/** 202 Accepted: o job nasce PENDING e deve ser acompanhado por polling. */
export async function generateReport(code: string, body: ReportGenerateRequest): Promise<ReportJob> {
  return (await api.post<ReportJob>(`/reports/${encodeURIComponent(code)}/generate`, body)).data
}

export async function getReportJob(id: UUID): Promise<ReportJob> {
  return (await api.get<ReportJob>(`/reports/jobs/${id}`)).data
}

export async function listMyReportJobs(paging: PageParams): Promise<Page<ReportJob>> {
  return (await api.get<Page<ReportJob>>('/reports/jobs/me', { params: pageQuery(paging) })).data
}

export async function downloadReport(id: UUID): Promise<Blob> {
  return (await api.get<Blob>(`/reports/jobs/${id}/download`, { responseType: 'blob' })).data
}
