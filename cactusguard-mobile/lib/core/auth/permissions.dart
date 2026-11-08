import 'auth_models.dart';

/// Conveniência de UI: espelha `cactusguard-web/src/lib/permissions.ts` (que espelha os @PreAuthorize
/// do backend). O backend continua sendo a barreira real; AUDITOR e ADMIN são somente leitura em
/// alertas/cases.
enum AppAction {
  alertAssume('alert:assume'),
  alertEscalate('alert:escalate'),
  alertFalsePositive('alert:falsePositive'),
  caseOpen('case:open'),
  caseDecide('case:decide'),
  caseEscalate('case:escalate'),
  caseApprove('case:approve'),
  caseNote('case:note'),
  caseUpload('case:upload'),
  piiReveal('pii:reveal'),
  auditRead('audit:read'),
  partyWrite('party:write'),
  pepMark('pep:mark'),
  pepApprove('pep:approve'),
  riskRecalculate('risk:recalculate'),
  screeningRun('screening:run'),
  screeningReview('screening:review'),
  watchlistWrite('watchlist:write'),
  kycWrite('kyc:write'),
  kycApprove('kyc:approve'),
  usersManage('users:manage'),
  siscoafAct('siscoaf:act'),
  ruleWrite('rule:write'),
  ruleApprove('rule:approve'),
  ruleRun('rule:run');

  const AppAction(this.code);
  final String code;
}

const _analysts = [Role.analyst, Role.supervisor];

const Map<AppAction, List<Role>> permissionMatrix = {
  AppAction.alertAssume: _analysts,
  AppAction.alertEscalate: _analysts,
  AppAction.alertFalsePositive: _analysts,
  AppAction.caseOpen: _analysts,
  AppAction.caseDecide: _analysts,
  AppAction.caseEscalate: _analysts,
  AppAction.caseApprove: [Role.supervisor],
  AppAction.caseNote: _analysts,
  AppAction.caseUpload: _analysts,
  AppAction.piiReveal: _analysts,
  AppAction.auditRead: [Role.auditor, Role.admin, Role.supervisor],
  AppAction.partyWrite: _analysts,
  AppAction.pepMark: _analysts,
  AppAction.pepApprove: [Role.supervisor],
  AppAction.riskRecalculate: [Role.supervisor, Role.admin],
  AppAction.screeningRun: _analysts,
  AppAction.screeningReview: _analysts,
  AppAction.watchlistWrite: [Role.supervisor],
  AppAction.kycWrite: _analysts,
  AppAction.kycApprove: [Role.supervisor],
  AppAction.usersManage: [Role.admin],
  AppAction.siscoafAct: [Role.supervisor],
  AppAction.ruleWrite: [Role.supervisor, Role.admin],
  AppAction.ruleApprove: [Role.supervisor],
  AppAction.ruleRun: [Role.supervisor, Role.admin],
};

bool hasAnyRole(Iterable<Role> userRoles, Iterable<Role> allowed) => userRoles.any(allowed.contains);

bool can(Iterable<Role> userRoles, AppAction action) => hasAnyRole(userRoles, permissionMatrix[action]!);
