import type { ISODateTime, UUID } from './common'

export interface Transaction {
  id: UUID
  partyId: UUID
  partyName: string | null
  type: string
  amount: number
  currency: string
  counterpartyName: string | null
  /** mascarado pela API */
  counterpartyDocument: string | null
  channel: string | null
  status: string
  transactionDate: ISODateTime
  originCountry: string | null
  destinationCountry: string | null
  purpose: string | null
  /** Backend (TransactionRequest) aceita IN|OUT; mocks/spec usam CREDIT|DEBIT. A UI trata os dois. */
  direction: 'IN' | 'OUT' | 'CREDIT' | 'DEBIT' | string | null
  cash: boolean
  amountBrl: number | null
  createdAt: ISODateTime
  /** PIX, TED, DOC, BOLETO, CASH, CARD, FX, CRYPTO, INTERNAL: existe no request; ainda não no response. */
  paymentMethod?: string | null
  counterpartyCountry?: string | null
}
