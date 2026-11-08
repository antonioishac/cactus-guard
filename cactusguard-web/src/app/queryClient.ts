import { MutationCache, QueryClient } from '@tanstack/react-query'
import { toApiError } from '@/api/errors'
import { toast } from '@/stores/toastStore'

declare module '@tanstack/react-query' {
  interface Register {
    mutationMeta: { /** não exibe toast automático de erro (o chamador trata) */ silent?: boolean; successMessage?: string }
  }
}

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        refetchOnWindowFocus: false,
        // erros 4xx não melhoram com retry
        retry: (count, err) => {
          const s = toApiError(err).status
          return count < 2 && (s === 0 || s >= 500)
        },
      },
    },
    mutationCache: new MutationCache({
      onError: (err, _v, _c, mutation) => {
        if (mutation.meta?.silent) return
        const e = toApiError(err)
        if (e.status === 401) return // sessão expirada: tratado pelo fluxo de login
        toast.error(e.traceId ? `${e.message} (ref. ${e.traceId})` : e.message)
      },
      onSuccess: (_d, _v, _c, mutation) => {
        if (mutation.meta?.successMessage) toast.success(mutation.meta.successMessage)
      },
    }),
  })
}
