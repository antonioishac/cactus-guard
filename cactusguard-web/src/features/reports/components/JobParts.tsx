import { useState } from 'react'
import { Download, Loader2 } from 'lucide-react'
import { errorMessage } from '@/api/errors'
import { downloadReport } from '@/api/reports'
import type { ReportJob } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import type { Tone } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { toast } from '@/stores/toastStore'
import { jobStatusLabel } from '../polling'

const TONE: Record<string, Tone> = { PENDING: 'neutral', PROCESSING: 'info', COMPLETED: 'success', FAILED: 'danger' }

export function JobStatusBadge({ status }: { status: string }) {
  const running = status === 'PENDING' || status === 'PROCESSING'
  return (
    <Badge tone={TONE[status] ?? 'neutral'}>
      {running && <Loader2 size={12} className="mr-1 animate-spin" aria-hidden />}
      {jobStatusLabel(status)}
    </Badge>
  )
}

export function DownloadButton({ job }: { job: ReportJob }) {
  const [busy, setBusy] = useState(false)
  if (job.status !== 'COMPLETED' || !job.downloadable) return null
  async function go() {
    setBusy(true)
    try {
      const blob = await downloadReport(job.id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${job.reportCode}.${job.format === 'EXCEL' ? 'xlsx' : 'pdf'}`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }
  return (
    <Button variant="secondary" className="!px-2 !py-1 text-xs" loading={busy} onClick={() => void go()} aria-label={`Baixar ${job.reportCode} em ${job.format}`}>
      <Download size={14} aria-hidden /> Baixar
    </Button>
  )
}
