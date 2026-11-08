/** Validação de upload de documento KYC no cliente (o backend valida de novo). */
export const KYC_MAX_BYTES = 10 * 1024 * 1024
export const KYC_ALLOWED_MIME = ['application/pdf', 'image/jpeg', 'image/png']
export const KYC_ALLOWED_EXT = ['.pdf', '.jpg', '.jpeg', '.png']

/** Devolve a mensagem de erro, ou null se o arquivo é aceitável. */
export function validateKycFile(file: { name: string; size: number; type: string }): string | null {
  const lower = file.name.toLowerCase()
  if (!KYC_ALLOWED_EXT.some((e) => lower.endsWith(e)) || (file.type && !KYC_ALLOWED_MIME.includes(file.type))) {
    return 'Tipo de arquivo não permitido. Envie PDF, JPG ou PNG.'
  }
  if (file.size <= 0) return 'O arquivo está vazio.'
  if (file.size > KYC_MAX_BYTES) return 'Arquivo acima de 10 MB.'
  return null
}
