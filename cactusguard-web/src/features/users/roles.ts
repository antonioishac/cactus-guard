import type { Role } from '@/api/types'

export const ROLE_ORDER: Role[] = ['ANALYST', 'SUPERVISOR', 'ADMIN', 'AUDITOR']

/** Descrição curta de cada perfil (espelha os @PreAuthorize do backend). */
export const ROLE_INFO: Record<Role, { label: string; description: string }> = {
  ANALYST: { label: 'Analista', description: 'Analisa alertas, abre e decide cases, cadastra partes, executa e revisa screening. Não aprova decisões.' },
  SUPERVISOR: { label: 'Supervisor', description: 'Tudo do analista, mais aprovação de decisões (quatro olhos), aprovação de PEP e KYC, lista interna e regras.' },
  ADMIN: { label: 'Administrador', description: 'Gestão de usuários e perfis, leitura de auditoria e rotinas administrativas. Sem decisões de case.' },
  AUDITOR: { label: 'Auditor', description: 'Somente leitura, incluindo trilha de auditoria. Não altera nenhum registro.' },
}
