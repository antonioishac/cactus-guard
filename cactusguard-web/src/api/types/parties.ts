import type { ISODate, ISODateTime, UUID } from './common'

/**
 * PartyResponse (spec 046). PII (document, email, phone, motherName) chega SEMPRE mascarada.
 * Campos do bloco 046 são opcionais só por conveniência dos mocks/formulários; o backend os devolve.
 */
export interface Party {
  id: UUID
  type: 'PF' | 'PJ' | string
  document: string
  name: string
  tradeName: string | null
  email: string | null
  phone: string | null
  riskLevel: string | null
  birthDate: ISODate | null
  nationality: string | null
  occupation: string | null
  status: string
  createdAt: ISODateTime

  /* --- spec 046 --- */
  motherName?: string | null
  sourceType?: string | null
  partyRole?: string | null
  relationshipStartDate?: ISODate | null
  countryOfResidence?: string | null
  cboCode?: string | null
  receitaStatus?: string | null
  receitaCheckedAt?: ISODateTime | null
  incorporationDate?: ISODate | null
  cnaePrimary?: string | null
  cnaesSecondary?: string[] | null
  legalNatureCode?: string | null
  shareCapital?: number | null
  companySize?: string | null
  nonprofit?: boolean
  publiclyTraded?: boolean
  declaredMonthlyIncome?: number | null
  declaredNetWorth?: number | null
  annualRevenue?: number | null
  sourceOfFunds?: string | null
  sourceOfWealth?: string | null
  purposeOfRelationship?: string | null
  riskScore?: number | null
  riskCalculatedAt?: ISODateTime | null
  nextReviewDate?: ISODate | null
  eddRequired?: boolean
  uboIdentified?: boolean | null
  uboNotIdentifiedReason?: string | null
  pep?: boolean
  pepStatus?: string | null
  pepCategory?: string | null
  pepRole?: string | null
  pepStartDate?: ISODate | null
  pepEndDate?: ISODate | null
  pepExpiryDate?: ISODate | null
  pepApprovalAt?: ISODateTime | null
  /** Nome de quem aprovou a alta administração. */
  pepApprovalByName?: string | null
  assetFreezeActive?: boolean
  assetFreezeAt?: ISODateTime | null
  assetFreezeReference?: string | null
}

export type PiiField = 'document' | 'motherName' | 'email' | 'phone'

/** POST /parties/{id}/reveal (ANALYST/SUPERVISOR/AUDITOR). `reason` 10–500 caracteres; registra data_access_log. */
export interface RevealRequest {
  field?: PiiField
  fields?: PiiField[]
  reason: string
}
export interface RevealResponse {
  field: PiiField | null
  value: string | null
  values: Partial<Record<PiiField, string>>
}

export const REVEAL_REASON_MIN = 10
export const REVEAL_REASON_MAX = 500

export const PARTY_TYPES = ['PF', 'PJ'] as const
export const SOURCE_TYPES = ['ONBOARDED', 'MIGRATED'] as const
export const RISK_LEVELS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const

/**
 * Filtros de `GET /parties` (todos combináveis). `q` e `document` são PII em potencial: vão só na
 * requisição, nunca na URL do navegador. `riskLevel` (não `riskCategory`).
 */
export interface PartyFilters {
  q?: string
  document?: string
  type?: string
  riskLevel?: string
  pep?: boolean
  sourceType?: string
}

/**
 * PartyRequest. `document` é obrigatório só na criação e IMUTÁVEL: no PUT omita-o (ou envie o mascarado, que é ignorado).
 * Em PUT, email/phone/motherName com `*` ou null mantêm o valor atual; "" limpa.
 */
export interface PartyRequest {
  type: 'PF' | 'PJ'
  document?: string
  name: string
  tradeName?: string | null
  email?: string | null
  phone?: string | null
  birthDate?: ISODate | null
  nationality?: string | null
  occupation?: string | null
  /* --- spec 046 --- */
  motherName?: string | null
  partyRole?: string | null
  relationshipStartDate?: ISODate | null
  cnaesSecondary?: string[] | null
  legalNatureCode?: string | null
  nonprofit?: boolean
  publiclyTraded?: boolean
  cboCode?: string | null
  countryOfResidence?: string | null
  declaredMonthlyIncome?: number | null
  declaredNetWorth?: number | null
  sourceOfFunds?: string | null
  sourceOfWealth?: string | null
  purposeOfRelationship?: string | null
  incorporationDate?: ISODate | null
  cnaePrimary?: string | null
  annualRevenue?: number | null
  shareCapital?: number | null
  companySize?: string | null
}

/** PartyAddress (o backend ainda serializa a entity; usar só estes campos). */
export interface PartyAddress {
  id: UUID
  type: string | null
  street: string | null
  number: string | null
  complement: string | null
  neighborhood: string | null
  city: string | null
  state: string | null
  zipCode: string | null
  country: string | null
  primary: boolean
}

/** Resumo mascarado da parte relacionada (nunca a entity). */
export interface RelatedPartySummary {
  id: UUID
  name: string
  type: string | null
  document: string | null
  pep: boolean
  pepStatus: string | null
  riskLevel: string | null
}

export interface PartyRelationship {
  id: UUID
  relatedParty: RelatedPartySummary | null
  relationshipType: string
  description: string | null
  active: boolean
  validFrom: ISODate | null
  validTo: ISODate | null
  details: Record<string, unknown> | null
}

export interface OwnershipLink {
  name: string
  pct: number | null
}

/** BeneficialOwnerResponse (documento do sócio mascarado). */
export interface BeneficialOwner {
  id: UUID
  ownerDocument: string
  ownerName: string
  ownershipPct: number | null
  controlType: string | null
  pep?: boolean
  finalBeneficiary: boolean
  indirect: boolean
  nationality: string | null
  validFrom: ISODate | null
  validTo: ISODate | null
  ownerType?: 'PF' | 'PJ' | string | null
  /** Parte cadastrada correspondente ao sócio (permite navegar para a ficha). */
  ownerParty?: { id: UUID; name?: string } | null
  /** Cadeia do topo até a pessoa natural final. */
  ownershipChain?: OwnershipLink[] | null
}

export const PEP_CATEGORIES = ['NATIONAL', 'FOREIGN', 'INTERNATIONAL_ORG'] as const
export type PepCategory = (typeof PEP_CATEGORIES)[number]

/** PepRequest do backend. */
export interface PepRequest {
  category: PepCategory
  role?: string | null
  startDate?: ISODate | null
  endDate?: ISODate | null
  /** familiar ou estreito colaborador */
  related: boolean
  justification: string
}

export interface PepResponse {
  partyId: UUID
  pep: boolean
  pepStatus: string
  category: string | null
  role: string | null
  startDate: ISODate | null
  endDate: ISODate | null
  expiryDate: ISODate | null
  approved: boolean
  eddRequired: boolean
  riskLevel: string | null
  approvedAt: ISODateTime | null
  approvedByName: string | null
}

/** RiskHistory (o backend ainda serializa a entity; usar só estes campos). */
export interface RiskHistoryEntry {
  id: UUID
  previousScore: number | null
  newScore: number
  previousLevel: string | null
  newLevel: string
  reason: string | null
  createdAt: ISODateTime
}
