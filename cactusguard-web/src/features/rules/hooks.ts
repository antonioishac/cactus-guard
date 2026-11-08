import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { approveRule, createRule, getRule, getRuleMetrics, listRules, listRuleVersions, runRuleEngine, toggleRule, updateRule } from '@/api/rules'
import type { PageParams, RuleFilters, RuleRequest } from '@/api/types'

export const useRules = (filters: RuleFilters, paging: PageParams) =>
  useQuery({ queryKey: ['rules', 'list', filters, paging], queryFn: () => listRules(filters, paging), placeholderData: keepPreviousData })

export const useRule = (code: string | undefined) =>
  useQuery({ queryKey: ['rules', 'detail', code], queryFn: () => getRule(code!), enabled: !!code })

export const useRuleMetrics = (code: string | undefined) =>
  useQuery({ queryKey: ['rules', code, 'metrics'], queryFn: () => getRuleMetrics(code!), enabled: !!code })

export const useRuleVersions = (code: string | undefined) =>
  useQuery({ queryKey: ['rules', code, 'versions'], queryFn: () => listRuleVersions(code!), enabled: !!code })

function useRuleAction<V, R>(fn: (v: V) => Promise<R>, successMessage?: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    meta: successMessage ? { successMessage } : undefined,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['rules'] })
      void qc.invalidateQueries({ queryKey: ['audit'] })
    },
  })
}

export const useCreateRule = () => useRuleAction((body: RuleRequest) => createRule(body), 'Regra criada e enviada para aprovação.')
export const useUpdateRule = () =>
  useRuleAction((v: { code: string; body: RuleRequest }) => updateRule(v.code, v.body), 'Nova versão criada e enviada para aprovação.')
export const useApproveRule = () =>
  useRuleAction((v: { code: string; justification: string }) => approveRule(v.code, v.justification), 'Regra aprovada e ativada.')
export const useToggleRule = () =>
  useRuleAction((v: { code: string; active: boolean; justification: string }) => toggleRule(v.code, v.active, v.justification))

export const useRunEngine = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: runRuleEngine,
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['alerts'] }),
  })
}
