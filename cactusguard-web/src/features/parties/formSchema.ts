import { z } from 'zod'
import type { PartyRequest } from '@/api/types'
import { isValidDocument, onlyDigits } from '@/lib/document'

const optionalMoney = z
  .string()
  .optional()
  .refine((v) => !v || (!Number.isNaN(Number(v.replace(',', '.'))) && Number(v.replace(',', '.')) >= 0), 'Informe um valor numérico válido.')

/** Schema do cadastro. `editing` dispensa o documento (imutável e só conhecido mascarado). */
export function buildPartySchema(editing: boolean) {
  return z
    .object({
      type: z.enum(['PF', 'PJ']),
      document: z.string(),
      name: z.string().trim().min(3, 'Informe o nome completo (mín. 3 caracteres).'),
      tradeName: z.string().optional(),
      email: z.string().trim().optional().refine((v) => !v || v.includes('*') || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v), 'E-mail inválido.'),
      phone: z.string().optional(),
      birthDate: z.string().optional(),
      nationality: z.string().optional(),
      occupation: z.string().optional(),
      motherName: z.string().optional(),
      declaredMonthlyIncome: optionalMoney,
      declaredNetWorth: optionalMoney,
      sourceOfFunds: z.string().optional(),
      purposeOfRelationship: z.string().optional(),
      incorporationDate: z.string().optional(),
      cnaePrimary: z.string().optional(),
      annualRevenue: optionalMoney,
    })
    .superRefine((v, ctx) => {
      if (!editing && !isValidDocument(v.type, v.document)) {
        ctx.addIssue({ code: 'custom', path: ['document'], message: v.type === 'PF' ? 'CPF inválido (verifique os dígitos).' : 'CNPJ inválido (verifique os dígitos).' })
      }
      if (v.birthDate && new Date(v.birthDate).getTime() > Date.now()) {
        ctx.addIssue({ code: 'custom', path: ['birthDate'], message: 'A data não pode ser futura.' })
      }
    })
}
export type PartyFormValues = z.infer<ReturnType<typeof buildPartySchema>>

const num = (v: string | undefined) => (v ? Number(v.replace(',', '.')) : null)
const str = (v: string | undefined) => (v?.trim() ? v.trim() : null)

/**
 * `editing`: o documento é imutável e só é conhecido mascarado, então NÃO é enviado no PUT
 * (o backend ignora ausente/mascarado e responde 422 DOCUMENT_IMMUTABLE se vier diferente e completo).
 * E-mail/telefone/nome da mãe mascarados (`*`) são enviados como estão: o backend mantém o valor atual.
 */
export function toRequest(v: PartyFormValues, editing = false): PartyRequest {
  const pj = v.type === 'PJ'
  return {
    type: v.type,
    ...(editing ? {} : { document: onlyDigits(v.document) }),
    name: v.name.trim(),
    tradeName: pj ? str(v.tradeName) : null,
    email: str(v.email),
    phone: str(v.phone),
    birthDate: !pj ? str(v.birthDate) : null,
    nationality: !pj ? str(v.nationality) : null,
    occupation: !pj ? str(v.occupation) : null,
    motherName: !pj ? str(v.motherName) : null,
    declaredMonthlyIncome: !pj ? num(v.declaredMonthlyIncome) : null,
    declaredNetWorth: !pj ? num(v.declaredNetWorth) : null,
    sourceOfFunds: str(v.sourceOfFunds),
    purposeOfRelationship: str(v.purposeOfRelationship),
    incorporationDate: pj ? str(v.incorporationDate) : null,
    cnaePrimary: pj ? str(v.cnaePrimary) : null,
    annualRevenue: pj ? num(v.annualRevenue) : null,
  }
}

