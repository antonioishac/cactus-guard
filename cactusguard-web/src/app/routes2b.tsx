import type { ComponentType } from 'react'
import type { RouteObject } from 'react-router'
import { RequireRole } from '@/features/auth/guards'
import { AUDIT_ROLES, SISCOAF_ROLES, APPROVER_ROLES } from './nav'

/**
 * Rotas da etapa 2B (SISCOAF, Regras, Relatórios, Auditoria, Aprovações) com carregamento sob demanda
 * (code-splitting): cada página vira um chunk próprio.
 */
const page = <T extends Record<string, unknown>>(load: () => Promise<T>, name: keyof T): Pick<RouteObject, 'lazy'> => ({
  lazy: async () => ({ Component: (await load())[name] as ComponentType }),
})

export const routes2B: RouteObject[] = [
  {
    element: <RequireRole roles={SISCOAF_ROLES} />,
    children: [
      {
        path: 'siscoaf',
        handle: { crumb: 'SISCOAF' },
        children: [
          { index: true, ...page(() => import('@/features/siscoaf/pages/SiscoafListPage'), 'SiscoafListPage') },
          { path: 'declaracao-negativa', handle: { crumb: 'Declaração negativa' }, ...page(() => import('@/features/siscoaf/pages/NegativeDeclarationPage'), 'NegativeDeclarationPage') },
          { path: ':id', handle: { crumb: 'Detalhe' }, ...page(() => import('@/features/siscoaf/pages/SiscoafDetailPage'), 'SiscoafDetailPage') },
        ],
      },
    ],
  },
  {
    path: 'regras',
    handle: { crumb: 'Regras' },
    children: [
      { index: true, ...page(() => import('@/features/rules/pages/RuleListPage'), 'RuleListPage') },
      { path: 'nova', handle: { crumb: 'Nova regra' }, ...page(() => import('@/features/rules/pages/RuleFormPage'), 'RuleFormPage') },
      { path: ':code', handle: { crumb: 'Detalhe' }, ...page(() => import('@/features/rules/pages/RuleDetailPage'), 'RuleDetailPage') },
      { path: ':code/editar', handle: { crumb: 'Editar' }, ...page(() => import('@/features/rules/pages/RuleFormPage'), 'RuleFormPage') },
    ],
  },
  { path: 'relatorios', handle: { crumb: 'Relatórios' }, ...page(() => import('@/features/reports/pages/ReportsPage'), 'ReportsPage') },
  {
    element: <RequireRole roles={AUDIT_ROLES} />,
    children: [{ path: 'auditoria', handle: { crumb: 'Auditoria' }, ...page(() => import('@/features/audit/pages/AuditPage'), 'AuditPage') }],
  },
  {
    element: <RequireRole roles={APPROVER_ROLES} />,
    children: [{ path: 'aprovacoes', handle: { crumb: 'Fila de aprovação' }, ...page(() => import('@/features/approvals/ApprovalsPage'), 'ApprovalsPage') }],
  },
]
