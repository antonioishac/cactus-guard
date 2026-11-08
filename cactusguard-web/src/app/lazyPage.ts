import type { ComponentType } from 'react'
import type { RouteObject } from 'react-router'

/** Rota com carregamento sob demanda (code-splitting): cada página vira um chunk próprio. */
export const lazyPage = <T extends Record<string, unknown>>(load: () => Promise<T>, name: keyof T): Pick<RouteObject, 'lazy'> => ({
  lazy: async () => ({ Component: (await load())[name] as ComponentType }),
})
