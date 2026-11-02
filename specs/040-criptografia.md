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
- Retenção conforme política de PLD (mínimo 5 anos).

## Observações para manutenção

- Rotação de chave deve ser automatizada.
- Em ambiente de desenvolvimento, usar chave fixa (nunca em produção).
- Testar descriptografia após rotação de chave.
