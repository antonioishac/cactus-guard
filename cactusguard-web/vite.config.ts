/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { port: 3000 },
  build: {
    rolldownOptions: {
      output: {
        // vendors em chunks próprios: melhor cache e entrada menor (recharts já sai com o dashboard, que é lazy)
        codeSplitting: {
          groups: [
            { name: 'vendor-react', test: /node_modules[/](react|react-dom|scheduler)[/]/, priority: 30 },
            { name: 'vendor-router', test: /node_modules[/]react-router/, priority: 25 },
            { name: 'vendor-forms', test: /node_modules[/](react-hook-form|@hookform|zod)[/]/, priority: 20 },
            { name: 'vendor-data', test: /node_modules[/](@tanstack|axios)[/]/, priority: 20 },
          ],
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
    testTimeout: 20_000,
    hookTimeout: 60_000,
  },
})
