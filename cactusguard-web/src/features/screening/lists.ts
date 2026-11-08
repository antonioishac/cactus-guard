import type { ScreeningListInfo } from '@/api/types'

/**
 * DIVERGÊNCIA: a entity ScreeningList existe mas não há controller de catálogo. Enquanto isso a tela mostra
 * este catálogo descritivo (spec 011); os códigos efetivamente aceitos em `lists` dependem do seed do backend.
 */
export const FALLBACK_LISTS: ScreeningListInfo[] = [
  { code: 'PEP', name: 'PEP (pessoas expostas politicamente)', type: 'PEP', source: 'Interna + provedores', requiresAssetFreeze: false, active: true, lastUpdated: null },
  { code: 'SANCTIONS', name: 'Sanções consolidadas', type: 'SANCTIONS', source: 'OFAC, ONU, UE', requiresAssetFreeze: false, active: true, lastUpdated: null },
  { code: 'OFAC', name: 'OFAC (EUA)', type: 'SANCTIONS', source: 'US Treasury', requiresAssetFreeze: false, active: true, lastUpdated: null },
  { code: 'CSNU', name: 'ONU / CSNU', type: 'SANCTIONS', source: 'Conselho de Segurança da ONU', requiresAssetFreeze: true, active: true, lastUpdated: null },
  { code: 'EU', name: 'União Europeia', type: 'SANCTIONS', source: 'EU Consolidated List', requiresAssetFreeze: false, active: true, lastUpdated: null },
  { code: 'GAFI', name: 'GAFI: jurisdições de alto risco', type: 'JURISDICTION', source: 'FATF/GAFI', requiresAssetFreeze: false, active: true, lastUpdated: null },
  { code: 'INTERNAL', name: 'Lista interna (watchlist)', type: 'INTERNAL', source: 'Instituição', requiresAssetFreeze: false, active: true, lastUpdated: null },
]

