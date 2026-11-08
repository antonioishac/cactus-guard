import type { ISODate, ISODateTime, UUID } from './common'

/**
 * PartyResponse do backend expõe apenas o bloco "core". Os campos do catálogo spec 046 que a entity
 * já possui mas o DTO não expõe estão como opcionais (nomes iguais aos da entity `Party`).
 * PII (document, email, phone, motherName) chega mascarada.
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

  /* --- spec 046 (opcionais: ainda não no DTO; nomes da entity Party) --- */
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
  /** Fatores do score — PROPOSTO (backend ainda não devolve). */
  riskFactors?: RiskFactor[] | null
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
  /** Nome de quem aprovou (a entity tem o User; o DTO deve achatar). */
  pepApprovalByName?: string | null
  assetFreezeActive?: boolean
  assetFreezeAt?: ISODateTime | null
  assetFreezeReference?: string | null
}

export interface RiskFactor {
  code: string
  label: string
  weight?: number | null
}

export type PiiField = 'document' | 'motherName' | 'email' | 'phone'

/** Contrato PROPOSTO (não existe no backend): revela um campo de PII e registra DataAccessLog. */
export interface RevealRequest {
  field: PiiField
  reason: string
}
export interface RevealResponse {
  field: PiiField
  value: string
}

export const PARTY_TYPES = ['PF', 'PJ'] as const
export const SOURCE_TYPES = ['ONBOARDED', 'MIGRATED'] as const
export const RISK_LEVELS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const

/**
 * Filtros da listagem. Backend: `GET /parties` (só paginação) e `GET /parties/search?q=`.
 * DIVERGÊNCIA: os demais filtros (spec 033/026) são PROPOSTOS; `document`/`q` nunca vão para a URL do navegador.
 */
export interface PartyFilters {
  q?: string
  document?: string
  type?: string
  riskLevel?: string
  pep?: boolean
  sourceType?: string
}

/** PartyRequest do backend (campos extras da spec 046 são enviados, mas o backend atual os ignora). */
export interface PartyRequest {
  type: 'PF' | 'PJ'
  document: string
  name: string
  tradeName?: string | null
  email?: string | null
  phone?: string | null
  birthDate?: ISODate | null
  nationality?: string | null
  occupation?: string | null
  /* --- spec 046 (ignorados pelo backend atual) --- */
  motherName?: string | null
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

/** Entity PartyAddress serializada direto pelo controller. */
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

/** Entity PartyRelationship serializada direto pelo controller (relatedParty é a entity Party). */
export interface PartyRelationship {
  id: UUID
  relatedParty: { id: UUID; name: string; type?: string; pep?: boolean; pepStatus?: string | null } | null
  relationshipType: string
  description: string | null
  active: boolean
  validFrom: ISODate | null
  validTo: ISODate | null
  details?: Record<string, unknown> | null
}

export interface OwnershipLink {
  name: string
  pct: number | null
  document_hash?: string
}

/** Entity BeneficialOwner serializada direto pelo controller. */
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
}

/** Entity RiskHistory serializada direto pelo controller (sem DTO). */
export interface RiskHistoryEntry {
  id: UUID
  previousScore: number | null
  newScore: number
  previousLevel: string | null
  newLevel: string
  reason: string | null
  createdAt: ISODateTime
}
