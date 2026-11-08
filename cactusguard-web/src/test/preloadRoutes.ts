/**
 * Pré-carrega TODAS as páginas lazy (code-splitting). Sem isso, o primeiro `render` de cada arquivo de teste
 * paga a transformação dos módulos e estoura o timeout de `findBy*` quando a máquina está carregada
 * (causa real da falha intermitente de `siscoaf.test.tsx`: a rota nem tinha começado a buscar dados em 1 s).
 */
export const preloadLazyRoutes = () =>
  Promise.all([
    import('@/features/dashboard/pages/DashboardPage'),
    import('@/features/alerts/pages/AlertListPage'),
    import('@/features/alerts/pages/AlertDetailPage'),
    import('@/features/cases/pages/CaseListPage'),
    import('@/features/cases/pages/CaseDossierPage'),
    import('@/features/parties/pages/PartyListPage'),
    import('@/features/parties/pages/PartyDetailPage'),
    import('@/features/parties/pages/PartyFormPage'),
    import('@/features/screening/pages/ScreeningPage'),
    import('@/features/users/pages/UsersPage'),
    import('@/features/siscoaf/pages/SiscoafListPage'),
    import('@/features/siscoaf/pages/SiscoafDetailPage'),
    import('@/features/siscoaf/pages/NegativeDeclarationPage'),
    import('@/features/rules/pages/RuleListPage'),
    import('@/features/rules/pages/RuleDetailPage'),
    import('@/features/rules/pages/RuleFormPage'),
    import('@/features/reports/pages/ReportsPage'),
    import('@/features/audit/pages/AuditPage'),
    import('@/features/approvals/ApprovalsPage'),
  ])
