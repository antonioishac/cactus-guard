# Spec 040 — Criptografia de Dados Sensíveis

## Objetivo

Proteger dados sensíveis em repouso e em trânsito, conforme LGPD e requisitos de PLD.

## Dados sensíveis

| Campo | Tabela | Tratamento |
|-------|--------|------------|
| `document` | parties | Criptografado em repouso |
| `email` | parties | Criptografado em repouso |
| `phone` | parties | Criptografado em repouso |
| `password` | users | Hash BCrypt (já implementado) |
| `payload` | siscoaf_communications | Criptografado se contiver dados pessoais |
| `raw_data` | migration_records | Criptografado |

## Criptografia em repouso

Usar **AES-256-GCM** com chave gerenciada externamente (AWS KMS, Azure Key Vault ou HashiCorp Vault).

### Estratégia

- Campo criptografado armazenado como `bytea` ou `text` (base64).
- Chave de criptografia rotacionada a cada 90 dias.
- Índice de busca por hash (não pelo valor criptografado).
- **Hash de documento por tenant**: `document_hash` = HMAC-SHA256 com chave derivada por HKDF-SHA256 de `DOCUMENT_HASH_KEY` + `tenant:<id>` (`DocumentHash.of` usa o tenant do contexto; sem tenant = exceção). O mesmo CPF gera hashes distintos em tenants distintos (sem correlação entre clientes em caso de vazamento). Versão em `tenants.hash_version` (1 = pepper global legado, 2 = por tenant); migração automática única na subida e `POST /api/v1/admin/rehash-documents` recalcula o tenant do chamador (também após rotação do segredo mestre).

### Implementação

```java
@Convert(converter = EncryptedStringConverter.class)
@Column(name = "document")
private String document;
```

## Criptografia em trânsito

- TLS 1.3 obrigatório em produção.
- Certificado válido (Let's Encrypt ou corporativo).
- HSTS habilitado.

## Logs

- Nunca logar documento, email ou telefone em texto claro.
- Usar mascaramento: `***.***.***-**` para CPF.

## Backup

- Backups criptografados com chave separada.
- Retenção conforme política de PLD: **10 anos** (Circular BCB 3.978/2020, art. 67 — mais conservador que os 5 anos do art. 10, §2º, da Lei 9.613/1998). Ver spec 003.

## Observações para manutenção

- Rotação de chave deve ser automatizada.
- Em ambiente de desenvolvimento, usar chave fixa (nunca em produção).
- Testar descriptografia após rotação de chave.
