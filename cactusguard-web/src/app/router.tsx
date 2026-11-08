import { createBrowserRouter, createMemoryRouter } from 'react-router'
import type { RouteObject } from 'react-router'
import AppLayout from '@/layouts/AppLayout'
import { RequireAuth, RequireRole } from '@/features/auth/guards'
import { LoginPage } from '@/features/auth/LoginPage'
import { DashboardPage } from '@/features/dashboard/pages/DashboardPage'
import { AlertListPage } from '@/features/alerts/pages/AlertListPage'
import { AlertDetailPage } from '@/features/alerts/pages/AlertDetailPage'
import { CaseListPage } from '@/features/cases/pages/CaseListPage'
import { CaseDossierPage } from '@/features/cases/pages/CaseDossierPage'
import { PartyListPage } from '@/features/parties/pages/PartyListPage'
import { PartyDetailPage } from '@/features/parties/pages/PartyDetailPage'
import { PartyFormPage } from '@/features/parties/pages/PartyFormPage'
import { ScreeningPage } from '@/features/screening/pages/ScreeningPage'
import { UsersPage } from '@/features/users/pages/UsersPage'
import { ADMIN_ROLES, NAV_ITEMS } from './nav'
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
          { index: true, element: <DashboardPage /> },
          {
            path: 'alertas',
            handle: { crumb: 'Alertas' },
            children: [
              { index: true, element: <AlertListPage /> },
              { path: ':id', element: <AlertDetailPage />, handle: { crumb: 'Detalhe' } },
            ],
          },
          {
            path: 'casos',
            handle: { crumb: 'Casos' },
            children: [
              { index: true, element: <CaseListPage /> },
              { path: ':id', element: <CaseDossierPage />, handle: { crumb: 'Dossiê' } },
            ],
          },
          {
            path: 'partes',
            handle: { crumb: 'Partes (KYC/PEP)' },
            children: [
              { index: true, element: <PartyListPage /> },
              { path: 'nova', element: <PartyFormPage />, handle: { crumb: 'Nova parte' } },
              { path: ':id', element: <PartyDetailPage />, handle: { crumb: 'Ficha 360º' } },
              { path: ':id/editar', element: <PartyFormPage />, handle: { crumb: 'Editar' } },
            ],
          },
          { path: 'screening', element: <ScreeningPage />, handle: { crumb: 'Screening' } },
          {
            element: <RequireRole roles={ADMIN_ROLES} />,
            children: [{ path: 'usuarios', element: <UsersPage />, handle: { crumb: 'Usuários' } }],
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
