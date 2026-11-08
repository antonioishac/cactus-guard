import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { errorMessage } from '@/api/errors'
import type { Party } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { ErrorState, Skeleton } from '@/components/ui/Feedback'
import { Card, PageHeader } from '@/components/ui/Layout'
import { formatDocument } from '@/lib/document'
import { buildPartySchema, toRequest } from '../formSchema'
import type { PartyFormValues } from '../formSchema'
import { useCreateParty, useParty, useUpdateParty } from '../hooks'

const CONTROL = 'w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm disabled:bg-neutral-bg'

function Field({ id, label, error, hint, children }: { id: string; label: string; error?: string; hint?: string; children: ReactNode }) {
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium">{label}</label>
      {children}
      {hint && !error && <p className="mt-1 text-xs text-muted">{hint}</p>}
      {error && <p id={`${id}-err`} role="alert" className="mt-1 text-xs text-danger">{error}</p>}
    </div>
  )
}

function defaultsFrom(p?: Party): PartyFormValues {
  const s = (v: string | null | undefined) => v ?? ''
  const n = (v: number | null | undefined) => (v == null ? '' : String(v))
  return {
    type: p?.type === 'PJ' ? 'PJ' : 'PF',
    document: s(p?.document),
    name: s(p?.name),
    tradeName: s(p?.tradeName),
    email: s(p?.email),
    phone: s(p?.phone),
    birthDate: s(p?.birthDate),
    nationality: s(p?.nationality) || 'BR',
    occupation: s(p?.occupation),
    motherName: s(p?.motherName),
    declaredMonthlyIncome: n(p?.declaredMonthlyIncome),
    declaredNetWorth: n(p?.declaredNetWorth),
    sourceOfFunds: s(p?.sourceOfFunds),
    purposeOfRelationship: s(p?.purposeOfRelationship),
    incorporationDate: s(p?.incorporationDate),
    cnaePrimary: s(p?.cnaePrimary),
    annualRevenue: n(p?.annualRevenue),
  }
}

function PartyForm({ party }: { party?: Party }) {
  const editing = !!party
  const navigate = useNavigate()
  const create = useCreateParty()
  const update = useUpdateParty(party?.id ?? '')
  const { register, handleSubmit, control, setValue, setError, reset, formState: { errors, isSubmitting } } = useForm<PartyFormValues>({
    resolver: zodResolver(buildPartySchema(editing)),
    defaultValues: defaultsFrom(party),
  })
  useEffect(() => reset(defaultsFrom(party)), [party, reset])
  const type = useWatch({ control, name: 'type' })
  const pj = type === 'PJ'
  const e = (k: keyof PartyFormValues) => errors[k]?.message
  const invalid = (k: keyof PartyFormValues) => (errors[k] ? true : undefined)

  const submit = handleSubmit(async (v) => {
    try {
      const saved = editing ? await update.mutateAsync(toRequest(v)) : await create.mutateAsync(toRequest(v))
      void navigate(`/partes/${saved.id}`)
    } catch (err) {
      setError('root', { message: errorMessage(err) })
    }
  })

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      <Card title="Identificação">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="pf-type" label="Tipo de pessoa">
            <select id="pf-type" {...register('type')} disabled={editing} className={CONTROL}>
              <option value="PF">Pessoa física</option>
              <option value="PJ">Pessoa jurídica</option>
            </select>
          </Field>
          <Field id="pf-doc" label={pj ? 'CNPJ' : 'CPF'} error={e('document')} hint={editing ? 'O documento não pode ser alterado.' : undefined}>
            <input
              id="pf-doc"
              inputMode="numeric"
              autoComplete="off"
              disabled={editing}
              aria-invalid={invalid('document')}
              {...register('document', { onChange: (ev) => setValue('document', formatDocument(type, ev.target.value)) })}
              className={CONTROL}
            />
          </Field>
          <Field id="pf-name" label={pj ? 'Razão social' : 'Nome completo'} error={e('name')}>
            <input id="pf-name" aria-invalid={invalid('name')} {...register('name')} className={CONTROL} />
          </Field>
          {pj && (
            <Field id="pf-trade" label="Nome fantasia">
              <input id="pf-trade" {...register('tradeName')} className={CONTROL} />
            </Field>
          )}
          <Field id="pf-email" label="E-mail" error={e('email')} hint={editing ? 'Valor mascarado: altere apenas para substituir.' : undefined}>
            <input id="pf-email" type="text" autoComplete="off" aria-invalid={invalid('email')} {...register('email')} className={CONTROL} />
          </Field>
          <Field id="pf-phone" label="Telefone" hint={editing ? 'Valor mascarado: altere apenas para substituir.' : undefined}>
            <input id="pf-phone" autoComplete="off" {...register('phone')} className={CONTROL} />
          </Field>
        </div>
      </Card>

      {!pj ? (
        <Card title="Dados da pessoa física">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field id="pf-mother" label="Nome da mãe"><input id="pf-mother" autoComplete="off" {...register('motherName')} className={CONTROL} /></Field>
            <Field id="pf-birth" label="Data de nascimento" error={e('birthDate')}><input id="pf-birth" type="date" {...register('birthDate')} className={CONTROL} /></Field>
            <Field id="pf-nat" label="Nacionalidade"><input id="pf-nat" {...register('nationality')} className={CONTROL} /></Field>
            <Field id="pf-occ" label="Ocupação"><input id="pf-occ" {...register('occupation')} className={CONTROL} /></Field>
            <Field id="pf-income" label="Renda mensal declarada (R$)" error={e('declaredMonthlyIncome')}><input id="pf-income" inputMode="decimal" {...register('declaredMonthlyIncome')} className={CONTROL} /></Field>
            <Field id="pf-worth" label="Patrimônio declarado (R$)" error={e('declaredNetWorth')}><input id="pf-worth" inputMode="decimal" {...register('declaredNetWorth')} className={CONTROL} /></Field>
          </div>
        </Card>
      ) : (
        <Card title="Dados da pessoa jurídica">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field id="pf-inc" label="Data de abertura"><input id="pf-inc" type="date" {...register('incorporationDate')} className={CONTROL} /></Field>
            <Field id="pf-cnae" label="CNAE principal"><input id="pf-cnae" placeholder="0000-0/00" {...register('cnaePrimary')} className={CONTROL} /></Field>
            <Field id="pf-rev" label="Faturamento anual (R$)" error={e('annualRevenue')}><input id="pf-rev" inputMode="decimal" {...register('annualRevenue')} className={CONTROL} /></Field>
          </div>
        </Card>
      )}

      <Card title="Origem dos recursos e propósito">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="pf-src" label="Origem dos recursos"><input id="pf-src" {...register('sourceOfFunds')} className={CONTROL} /></Field>
          <Field id="pf-purpose" label="Propósito do relacionamento"><input id="pf-purpose" {...register('purposeOfRelationship')} className={CONTROL} /></Field>
        </div>
      </Card>

      {errors.root && <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">{errors.root.message}</p>}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={() => void navigate(-1)} disabled={isSubmitting}>Cancelar</Button>
        <Button type="submit" loading={isSubmitting}>{editing ? 'Salvar alterações' : 'Cadastrar parte'}</Button>
      </div>
    </form>
  )
}

export function PartyFormPage() {
  const { id } = useParams()
  const q = useParty(id)
  return (
    <>
      <PageHeader title={id ? 'Editar parte' : 'Nova parte'} subtitle="Campos conforme o catálogo PLD (spec 046)" />
      {id && q.isLoading && <Skeleton className="h-64 w-full" />}
      {id && q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
      {(!id || q.data) && <PartyForm party={q.data} />}
    </>
  )
}
