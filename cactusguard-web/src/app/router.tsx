import { createBrowserRouter, createMemoryRouter } from 'react-router'
import type { RouteObject } from 'react-router'
import AppLayout from '@/layouts/AppLayout'
import { RequireAuth, RequireRole } from '@/features/auth/guards'
import { LoginPage } from '@/features/auth/LoginPage'
import { ADMIN_ROLES, NAV_ITEMS } from './nav'
import { lazyPage } from './lazyPage'
import { routes2B } from './routes2b'
import { NotFound, Unauthorized, UnderConstruction } from './statusPages'

// Módulos da etapa 2: uma rota placeholder por item "não pronto" do menu, já com guarda de role.
const placeholders: RouteObject[] = NAV_ITEMS.filter((i) => !i.ready).map((i) => {
  const route: RouteObject = { path: i.path.slice(1), element: <UnderConstruction />, handle: { crumb: i.label } }
  return i.roles ? { element: <RequireRole roles={i.roles} />, children: [route] } : route
})

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/unauthorized', element: <Unauthorized /> },
      {
        path: '/',
        element: <AppLayout />,
        handle: { crumb: 'Início' },
        children: [
          { index: true, ...lazyPage(() => import('@/features/dashboard/pages/DashboardPage'), 'DashboardPage') },
          {
            path: 'alertas',
            handle: { crumb: 'Alertas' },
            children: [
              { index: true, ...lazyPage(() => import('@/features/alerts/pages/AlertListPage'), 'AlertListPage') },
              { path: ':id', ...lazyPage(() => import('@/features/alerts/pages/AlertDetailPage'), 'AlertDetailPage'), handle: { crumb: 'Detalhe' } },
            ],
          },
          {
            path: 'casos',
            handle: { crumb: 'Casos' },
            children: [
              { index: true, ...lazyPage(() => import('@/features/cases/pages/CaseListPage'), 'CaseListPage') },
              { path: ':id', ...lazyPage(() => import('@/features/cases/pages/CaseDossierPage'), 'CaseDossierPage'), handle: { crumb: 'Dossiê' } },
            ],
          },
          {
            path: 'partes',
            handle: { crumb: 'Partes (KYC/PEP)' },
            children: [
              { index: true, ...lazyPage(() => import('@/features/parties/pages/PartyListPage'), 'PartyListPage') },
              { path: 'nova', ...lazyPage(() => import('@/features/parties/pages/PartyFormPage'), 'PartyFormPage'), handle: { crumb: 'Nova parte' } },
              { path: ':id', ...lazyPage(() => import('@/features/parties/pages/PartyDetailPage'), 'PartyDetailPage'), handle: { crumb: 'Ficha 360º' } },
              { path: ':id/editar', ...lazyPage(() => import('@/features/parties/pages/PartyFormPage'), 'PartyFormPage'), handle: { crumb: 'Editar' } },
            ],
          },
          { path: 'screening', ...lazyPage(() => import('@/features/screening/pages/ScreeningPage'), 'ScreeningPage'), handle: { crumb: 'Screening' } },
          {
            element: <RequireRole roles={ADMIN_ROLES} />,
            children: [{ path: 'usuarios', ...lazyPage(() => import('@/features/users/pages/UsersPage'), 'UsersPage'), handle: { crumb: 'Usuários' } }],
          },
          ...routes2B,
          ...placeholders,
          { path: '*', element: <NotFound /> },
        ],
      },
    ],
  },
]

export const createAppRouter = () => createBrowserRouter(routes)
export const createTestRouter = (initialEntries: string[]) => createMemoryRouter(routes, { initialEntries })
