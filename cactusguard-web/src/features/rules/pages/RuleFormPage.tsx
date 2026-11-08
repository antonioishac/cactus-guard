import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import type { MonitoringRule, RuleRequest } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { ErrorState, Skeleton } from '@/components/ui/Feedback'
import { JustificationDialog } from '@/components/ui/JustificationDialog'
import { Card, PageHeader } from '@/components/ui/Layout'
import { severityLabel } from '@/lib/format'
import { ParamsEditor } from '../components/ParamsEditor'
import { fieldsToParams, paramsToFields, validateFields } from '../params'
import type { ParamField } from '../params'
import { useCreateRule, useRule, useUpdateRule } from '../hooks'
import { CATEGORY_LABEL } from '../ui-rules'

const RISK_LEVELS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const optionalNumber = z.string().refine((v) => v.trim() === '' || (Number.isFinite(Number(v.replace(',', '.'))) && Number(v.replace(',', '.')) >= 0), 'Informe um número maior ou igual a zero.')

const schema = z.object({
  code: z.string().regex(/^[A-Z0-9_]{3,50}$/, 'Use 3 a 50 caracteres: letras maiúsculas, números e "_".').or(z.literal('')),
  name: z.string().trim().min(3, 'Informe o nome (mín. 3 caracteres).').max(200),
  description: z.string().max(1000),
  category: z.string().min(1, 'Selecione a categoria.'),
  severity: z.string().min(1, 'Selecione a severidade.'),
  thresholdValue: optionalNumber,
  thresholdPeriodDays: z.string().refine((v) => v.trim() === '' || /^\d+$/.test(v.trim()), 'Informe um número inteiro.'),
  carta4001Reference: z.string().max(300),
  appliesToRiskLevels: z.array(z.string()),
})
type FormValues = z.infer<typeof schema>

const CONTROL = 'w-full rounded-lg border border-line bg-surface px-2.5 py-1.5 text-sm'

function Field({ label, error, children, htmlFor }: { label: string; error?: string; children: React.ReactNode; htmlFor: string }) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1 block text-sm font-medium">{label}</label>
      {children}
      {error && <p role="alert" className="mt-1 text-xs text-danger">{error}</p>}
    </div>
  )
}

function toDefaults(r?: MonitoringRule | null): FormValues {
  return {
    code: r?.code ?? '',
    name: r?.name ?? '',
    description: r?.description ?? '',
    category: r?.category ?? '',
    severity: r?.severity ?? '',
    thresholdValue: r?.thresholdValue != null ? String(r.thresholdValue) : '',
    thresholdPeriodDays: r?.thresholdPeriodDays != null ? String(r.thresholdPeriodDays) : '',
    carta4001Reference: r?.carta4001Reference ?? '',
    appliesToRiskLevels: r?.appliesToRiskLevels ?? [],
  }
}

function RuleForm({ rule }: { rule: MonitoringRule | null }) {
  const editing = !!rule
  const navigate = useNavigate()
  const create = useCreateRule()
  const update = useUpdateRule()
  const [fields, setFields] = useState<ParamField[]>(() => paramsToFields(rule?.parameters))
  const [paramErrors, setParamErrors] = useState<Record<string, string>>({})
  const [pending, setPending] = useState<FormValues | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: toDefaults(rule) })

  const onValid = handleSubmit((v) => {
    if (!editing && !v.code) return setError('code', { message: 'O código é obrigatório.' })
    const pe = validateFields(fields)
    setParamErrors(pe)
    if (Object.keys(pe).length === 0) setPending(v)
  })

  function buildRequest(v: FormValues, reason: string): RuleRequest {
    return {
      code: editing ? undefined : v.code,
      name: v.name.trim(),
      description: v.description.trim() || undefined,
      category: v.category,
      severity: v.severity,
      thresholdValue: v.thresholdValue.trim() ? Number(v.thresholdValue.replace(',', '.')) : null,
      thresholdPeriodDays: v.thresholdPeriodDays.trim() ? Number(v.thresholdPeriodDays) : null,
      parameters: fieldsToParams(fields),
      appliesToRiskLevels: v.appliesToRiskLevels.length ? v.appliesToRiskLevels : undefined,
      carta4001Reference: v.carta4001Reference.trim() || undefined,
      reason,
    }
  }

  return (
    <>
      <PageHeader title={editing ? `Editar ${rule.code}` : 'Nova regra'} subtitle="A alteração gera uma nova versão que só vale após aprovação de outro supervisor." />
      <form onSubmit={(e) => void onValid(e)} noValidate className="space-y-4">
        <Card title="Dados da regra">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Código" htmlFor="r-code" error={errors.code?.message}>
              <input id="r-code" {...register('code')} disabled={editing} placeholder="EX.: CASH_50K" className={CONTROL} />
            </Field>
            <Field label="Nome" htmlFor="r-name" error={errors.name?.message}>
              <input id="r-name" {...register('name')} className={CONTROL} />
            </Field>
            <Field label="Categoria" htmlFor="r-cat" error={errors.category?.message}>
              <select id="r-cat" {...register('category')} className={CONTROL}>
                <option value="">Selecione</option>
                {Object.entries(CATEGORY_LABEL).map(([v, l]) => (
                  <option key={v} value={v}>{l}</option>
                ))}
              </select>
            </Field>
            <Field label="Severidade" htmlFor="r-sev" error={errors.severity?.message}>
              <select id="r-sev" {...register('severity')} className={CONTROL}>
                <option value="">Selecione</option>
                {['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) => (
                  <option key={s} value={s}>{severityLabel(s)}</option>
                ))}
              </select>
            </Field>
            <Field label="Limiar (valor)" htmlFor="r-th" error={errors.thresholdValue?.message}>
              <input id="r-th" inputMode="decimal" {...register('thresholdValue')} className={CONTROL} />
            </Field>
            <Field label="Janela (dias)" htmlFor="r-days" error={errors.thresholdPeriodDays?.message}>
              <input id="r-days" inputMode="numeric" {...register('thresholdPeriodDays')} className={CONTROL} />
            </Field>
            <div className="sm:col-span-2">
              <Field label="Cenário Carta Circular 4.001" htmlFor="r-carta" error={errors.carta4001Reference?.message}>
                <input id="r-carta" {...register('carta4001Reference')} className={CONTROL} />
              </Field>
            </div>
            <div className="sm:col-span-2">
              <Field label="Descrição" htmlFor="r-desc" error={errors.description?.message}>
                <textarea id="r-desc" rows={3} {...register('description')} className={CONTROL} />
              </Field>
            </div>
            <fieldset className="sm:col-span-2">
              <legend className="mb-1 text-sm font-medium">Aplica-se aos níveis de risco (vazio = todos)</legend>
              <div className="flex flex-wrap gap-4">
                {RISK_LEVELS.map((l) => (
                  <label key={l} className="flex items-center gap-1.5 text-sm">
                    <input type="checkbox" value={l} {...register('appliesToRiskLevels')} /> {severityLabel(l)}
                  </label>
                ))}
              </div>
            </fieldset>
          </div>
        </Card>
        <Card>
          <ParamsEditor fields={fields} errors={paramErrors} onChange={setFields} />
        </Card>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => void navigate(-1)}>Cancelar</Button>
          <Button type="submit">{editing ? 'Salvar nova versão' : 'Criar regra'}</Button>
        </div>
      </form>

      <JustificationDialog
        open={pending !== null}
        title={editing ? `Salvar nova versão de ${rule.code}` : 'Criar regra'}
        description="A regra ficará aguardando aprovação de outro supervisor antes de entrar em vigor. Informe o motivo da mudança."
        confirmLabel={editing ? 'Salvar e enviar para aprovação' : 'Criar e enviar para aprovação'}
        onCancel={() => setPending(null)}
        onConfirm={async ({ justification }) => {
          if (!pending) return
          const body = buildRequest(pending, justification)
          const saved = editing ? await update.mutateAsync({ code: rule.code, body }) : await create.mutateAsync(body)
          setPending(null)
          void navigate(`/regras/${encodeURIComponent(saved.code)}`)
        }}
      />
    </>
  )
}

export function RuleFormPage() {
  const { code } = useParams()
  const rule = useRule(code)
  if (!code) return <RuleForm rule={null} />
  if (rule.isLoading) return <Skeleton className="h-64 w-full" />
  if (rule.error) return <ErrorState error={rule.error} onRetry={() => void rule.refetch()} />
  if (!rule.data) return <p className="text-sm text-muted">Regra não localizada na listagem da API.</p>
  return <RuleForm rule={rule.data} />
}
