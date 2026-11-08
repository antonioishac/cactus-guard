/**
 * Verificação em tempo de compilação: cada tipo escrito à mão em `src/api/types` precisa ser compatível
 * com o schema gerado do OpenAPI real (`generated/schema.d.ts`, de `openapi.json`).
 *  - campo que o front declara mas o backend não tem   => erro (`extraKeys`)
 *  - tipo do campo não atribuível ao do backend        => erro (`badTypes`)
 * Se o backend mudar, rode `npm run api:types` e corrija os erros de `tsc` apontados aqui.
 * Não há código em tempo de execução: o arquivo só existe para o `tsc` (`npm run typecheck`).
 */
import type { components } from './generated/schema'
import type * as T from './types'

type S = components['schemas']

/** O backend pode devolver null em qualquer campo: o front modela isso com `| null` em todos os níveis. */
type Nullable<G> = G extends object ? { [K in keyof G]: Nullable<G[K]> | null } : G | null

type Extra<H, G> = Exclude<keyof H, keyof G>
type BadType<H, G> = {
  [K in keyof H & keyof G]-?: [H[K]] extends [Nullable<G[K]> | null | undefined] ? never : K
}[keyof H & keyof G]
type Check<H, G> = [Extra<H, G>] extends [never]
  ? [BadType<H, G>] extends [never]
    ? true
    : { badTypes: BadType<H, G> }
  : { extraKeys: Extra<H, G> }
type Expect<X extends true> = X

/** Itens de Page<T>: compara o tipo do elemento do `content`. */
type PageOf<P> = P extends { content?: (infer E)[] } ? E : never

export type ContractChecks = [
  // alertas
  Expect<Check<T.Alert, S['AlertResponse']>>,
  Expect<Check<T.AlertDetail, S['AlertDetailResponse']>>,
  Expect<Check<T.AlertEvidenceItem, S['AlertEvidenceItem']>>,
  Expect<Check<T.AlertHistoryEntry, S['AlertHistoryEntry']>>,
  Expect<Check<T.AlertRuleSummary, S['RuleSummary']>>,
  Expect<Check<T.AlertPartySummary, S['PartySummary']>>,
  Expect<Check<T.AlertComment, S['AlertCommentResponse']>>,
  Expect<Check<T.AlertEscalateRequest, S['AlertEscalationRequest']>>,
  Expect<Check<T.FalsePositiveRequest, S['FalsePositiveRequest']>>,
  // cases
  Expect<Check<T.Case, S['CaseResponse']>>,
  Expect<Check<T.CaseCreateRequest, S['CaseCreateRequest']>>,
  Expect<Check<T.CaseDecisionRequest, S['CaseDecisionRequest']>>,
  Expect<Check<T.CaseEscalationRequest, S['CaseEscalationRequest']>>,
  Expect<Check<T.CaseApprovalRequest, S['CaseApprovalRequest']>>,
  Expect<Check<T.CaseRejectApprovalRequest, S['CaseRejectApprovalRequest']>>,
  Expect<Check<T.CaseNote, S['CaseNoteResponse']>>,
  Expect<Check<T.CaseEvidence, S['CaseEvidenceResponse']>>,
  Expect<Check<T.CaseDossier, S['CaseDossierResponse']>>,
  Expect<Check<T.DossierAlert, S['AlertInfo']>>,
  Expect<Check<T.DossierRiskItem, S['RiskHistoryItem']>>,
  Expect<Check<T.DossierPep, S['PepInfo']>>,
  Expect<Check<T.DossierUbo, S['UboItem']>>,
  Expect<Check<T.DossierScreeningHit, S['ScreeningHitItem']>>,
  Expect<Check<T.DossierAuditItem, S['AuditItem']>>,
  // transações
  Expect<Check<T.Transaction, S['TransactionResponse']>>,
  Expect<Check<T.TransactionSummary, S['TransactionSummary']>>,
  // partes
  Expect<Check<T.Party, S['PartyResponse']>>,
  Expect<Check<T.PartyRequest, S['PartyRequest']>>,
  Expect<Check<T.PartyAddress, S['PartyAddress']>>,
  Expect<Check<T.PartyRelationship, S['PartyRelationshipResponse']>>,
  Expect<Check<T.RelatedPartySummary, S['RelatedPartySummary']>>,
  Expect<Check<T.BeneficialOwner, S['BeneficialOwnerResponse']>>,
  Expect<Check<T.PepRequest, S['PepRequest']>>,
  Expect<Check<T.PepResponse, S['PepResponse']>>,
  Expect<Check<T.RiskHistoryEntry, S['RiskHistory']>>,
  Expect<Check<T.RevealRequest, S['RevealRequest']>>,
  Expect<Check<T.RevealResponse, S['RevealResponse']>>,
  // KYC
  Expect<Check<T.KycProcess, S['KycProcessResponse']>>,
  Expect<Check<T.KycDocument, S['KycDocumentResponse']>>,
  Expect<Check<T.KycChecklistItem, S['KycChecklistResponse']>>,
  // screening / watchlist
  Expect<Check<T.ScreeningResult, S['ScreeningResultResponse']>>,
  Expect<Check<T.ScreeningReviewRequest, S['ScreeningReviewRequest']>>,
  Expect<Check<T.ScreeningListInfo, S['ScreeningListResponse']>>,
  Expect<Check<T.WatchlistEntry, S['WatchlistEntry']>>,
  // usuários / auth (tenantId/tenantName são preparação para multi-tenancy: fora do OpenAPI por enquanto)
  Expect<Check<T.UserAccount, S['UserResponse']>>,
  Expect<Check<T.UserCreateRequest, S['UserCreateRequest']>>,
  Expect<Check<T.UserUpdateRequest, S['UserUpdateRequest']>>,
  Expect<Check<T.PasswordResetRequest, S['PasswordResetRequest']>>,
  Expect<Check<T.PasswordResetResponse, S['PasswordResetResponse']>>,
  Expect<Check<Omit<T.AuthUserDto, 'tenantId' | 'tenantName'>, S['UserInfo']>>,
  Expect<Check<T.LoginRequest, S['LoginRequest']>>,
  // dashboard (operational/metrics não têm DTO no OpenAPI)
  Expect<Check<T.CaseSlaSummary, S['CaseSla']>>,
  Expect<Check<T.DeadlineBuckets, S['Buckets']>>,
  Expect<Check<T.AlertsChartPoint, S['ChartPoint']>>,
  Expect<Check<T.SeverityDistributionItem, S['SeverityCount']>>,
  // auditoria
  Expect<Check<T.AuditEntry, S['AuditLogResponse']>>,
  Expect<Check<T.DataAccessEntry, S['DataAccessLogResponse']>>,
  Expect<Check<T.UserSummary, S['UserSummary']>>,
  // SISCOAF
  Expect<Check<T.SiscoafCommunication, S['SiscoafResponse']>>,
  Expect<Check<T.SiscoafHistoryEntry, S['SiscoafHistoryEntry']>>,
  Expect<Check<T.SiscoafReasonRequest, S['SiscoafReasonRequest']>>,
  Expect<Check<T.SiscoafApproveRequest, S['SiscoafApproveRequest']>>,
  Expect<Check<T.SiscoafSubmitRequest, S['SiscoafSubmitRequest']>>,
  // regras
  Expect<Check<T.MonitoringRule, S['MonitoringRuleResponse']>>,
  Expect<Check<T.RuleRequest, S['MonitoringRuleRequest']>>,
  Expect<Check<T.RuleVersion, S['RuleVersionResponse']>>,
  Expect<Check<T.RuleMetrics, S['RuleMetricsResponse']>>,
  // fila de aprovação
  Expect<Check<T.PendingCaseItem, S['CaseItem']>>,
  Expect<Check<T.PendingRuleItem, S['RuleItem']>>,
  Expect<Check<T.PendingPepItem, S['PepItem']>>,
  // relatórios / notificações
  Expect<Check<T.ReportJob, S['ReportJobResponse']>>,
  Expect<Check<T.ReportDefinition, S['ReportDefinition']>>,
  Expect<Check<T.AppNotification, S['Notification']>>,
  // páginas: o elemento de cada Page precisa ser o mesmo DTO verificado acima
  Expect<Check<T.Case, PageOf<S['PageCaseResponse']>>>,
  Expect<Check<T.Alert, PageOf<S['PageAlertResponse']>>>,
  Expect<Check<T.Party, PageOf<S['PagePartyResponse']>>>,
  Expect<Check<T.SiscoafCommunication, PageOf<S['PageSiscoafResponse']>>>,
  Expect<Check<T.MonitoringRule, PageOf<S['PageMonitoringRuleResponse']>>>,
]
