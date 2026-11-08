import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { OUT, renderSchema } from '../../scripts/gen-api-types.mjs'

describe('contrato OpenAPI', () => {
  it('tipos gerados (schema.d.ts) estão em dia com openapi.json', async () => {
    const expected = (await renderSchema()) as string
    const current = readFileSync(OUT as string, 'utf8')
    // Falhou? O openapi.json mudou (ou o schema.d.ts foi editado à mão). Rode: npm run api:types
    expect(current.replace(/\r\n/g, '\n')).toBe(expected.replace(/\r\n/g, '\n'))
  }, 60_000)
})
