import { auditHandlers } from './audit'
import { notificationsHandlers } from './notifications'
import { reportsHandlers } from './reports'
import { rulesHandlers } from './rules'
import { siscoafHandlers } from './siscoaf'

/** Handlers da etapa 2B. Registrados no INÍCIO de `handlers` (o de auditoria sobrepõe o da etapa 1). */
export const handlers2B = [...auditHandlers, ...siscoafHandlers, ...rulesHandlers, ...reportsHandlers, ...notificationsHandlers]
