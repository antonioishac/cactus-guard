/** Pré-carrega as páginas lazy da etapa 2B para que o primeiro `findBy*` dos testes não estoure o timeout. */
export const preload2B = () =>
  Promise.all([
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
