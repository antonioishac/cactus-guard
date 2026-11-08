import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router/dom'
import './styles/index.css'
import { createQueryClient } from '@/app/queryClient'
import { createAppRouter } from '@/app/router'
import { Toaster } from '@/components/ui/Toast'
import { bootstrapSession } from '@/features/auth/session'

const useMock = import.meta.env.MODE === 'mock' || import.meta.env.VITE_USE_MOCK === 'true'

async function start() {
  if (useMock) {
    const { worker } = await import('@/mocks/browser')
    await worker.start()
  }
  void bootstrapSession()
  const queryClient = createQueryClient()
  const router = createAppRouter()
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
        <Toaster />
      </QueryClientProvider>
    </StrictMode>,
  )
}

void start()
