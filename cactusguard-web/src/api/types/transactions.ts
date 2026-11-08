import type { ISODateTime, UUID } from './common'

export type TransactionDirection = 'IN' | 'OUT'

/** TransactionResponse (backend). Documento da contraparte já vem mascarado. */
export interface Transaction {
  id: UUID
  partyId: UUID
  partyName: string | null
  type: string
  amount: number
  currency: string
  counterpartyName: string | null
  counterpartyDocument: string | null
  channel: string | null
  status: string
  transactionDate: ISODateTime
  originCountry: string | null
  destinationCountry: string | null
  purpose: string | null
  direction: TransactionDirection | null
  cash: boolean
  amountBrl: number | null
  createdAt: ISODateTime
  /** PIX, TED, DOC, BOLETO, CASH, CARD, FX, CRYPTO, INTERNAL */
  paymentMethod: string | null
  counterpartyCountry: string | null
  endToEndId: string | null
  externalReference: string | null
}

/** Resumo de transação em alerta/dossiê (sem conta/agência). */
export interface TransactionSummary {
  id: UUID
  type: string
  amount: number
  currency: string
  direction: TransactionDirection | null
  transactionDate: ISODateTime
  channel: string | null
  cash: boolean
  paymentMethod: string | null
  counterpartyName: string | null
  counterpartyDocument: string | null
  counterpartyCountry: string | null
  status: string
}
