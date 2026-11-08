import { http, HttpResponse } from 'msw'
import type { ReportGenerateRequest } from '@/api/types'
import { API, READ, err, guard, invalid, isResponse, jsonBody, paginate } from './helpers'
import { db2b } from './db2b'
import type { MockJob } from './db2b'


/** Cada consulta avança o job: PENDING -> PROCESSING -> COMPLETED (ou FAILED p/ período inválido). */
function tick(job: MockJob): MockJob {
  job.polls += 1
  if (job.status === 'PENDING') job.status = 'PROCESSING'
  else if (job.status === 'PROCESSING' && job.polls >= 3) {
    const bad = String(job.parameters?.from ?? '') < '2000-01-01'
    job.status = bad ? 'FAILED' : 'COMPLETED'
    job.completedAt = new Date().toISOString()
    job.downloadable = !bad
    if (bad) job.errorMessage = 'Não foi possível gerar o relatório. Contate o suporte informando a referência.'
  }
  return job
}
function publicJob(job: MockJob) {
  const { ownerId, polls, ...rest } = job
  void ownerId
  void polls
  return rest
}

export const reportsHandlers = [
  http.get(`${API}/reports`, ({ request }) => {
    const u = guard(request, READ)
    return isResponse(u) ? u : HttpResponse.json(db2b.defs)
  }),
  // rota estática antes de /jobs/:id
  http.get(`${API}/reports/jobs/me`, ({ request }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    const mine = db2b.jobs.filter((j) => j.ownerId === u.id)
    mine.forEach((j) => !['COMPLETED', 'FAILED'].includes(j.status) && tick(j))
    return HttpResponse.json(paginate(mine.map(publicJob), new URL(request.url)))
  }),
  http.get(`${API}/reports/jobs/:id/download`, ({ request, params }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    const j = db2b.jobs.find((x) => x.id === params.id)
    if (!j || j.status !== 'COMPLETED') return err(404, 'Arquivo não disponível')
    return new HttpResponse('relatorio-fake', { headers: { 'Content-Type': j.format === 'EXCEL' ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' : 'application/pdf' } })
  }),
  http.get(`${API}/reports/jobs/:id`, ({ request, params }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    const j = db2b.jobs.find((x) => x.id === params.id)
    return j ? HttpResponse.json(publicJob(tick(j))) : err(404, 'Job não encontrado')
  }),
  http.post(`${API}/reports/:code/generate`, async ({ request, params }) => {
    const u = guard(request, READ)
    if (isResponse(u)) return u
    const def = db2b.defs.find((d) => d.code === params.code)
    if (!def) return err(404, `Report not found: ${String(params.code)}`)
    const body = await jsonBody<ReportGenerateRequest>(request)
    if (!body.format || !['PDF', 'EXCEL'].includes(body.format)) return invalid('Dados inválidos', [{ field: 'format', message: 'deve ser PDF ou EXCEL' }])
    const job: MockJob = {
      id: `job-${db2b.jobs.length + 1}`, reportCode: def.code, status: 'PENDING', format: body.format!, parameters: body.parameters ?? null, downloadable: false, errorMessage: null,
      createdAt: new Date().toISOString(), completedAt: null, ownerId: u.id, polls: 0,
    }
    db2b.jobs.unshift(job)
    return HttpResponse.json(publicJob(job), { status: 202 })
  }),
]
