# Spec 037 — Integrações Externas (Screening, SISCOAF, KYC)

## Objetivo

Definir a arquitetura de integração com provedores externos para screening, comunicação ao SISCOAF e validação de KYC, de forma que o sistema possa operar com provedores reais sem alterar o domínio.

## Princípio

Usar **ports & adapters** (hexagonal). O domínio define a porta. O adapter implementa a comunicação com o provedor.

## Screening

### Porta

```java
public interface ScreeningProviderPort {
    ScreeningResult screen(Party party, List<String> listCodes);
    List<ScreeningList> availableLists();
}
```

### Adapters previstos

| Provedor | Uso | Observação |
|----------|-----|------------|
| Dow Jones | PEP + Sanções globais | Pago, alta cobertura |
| LexisNexis | PEP + Adverse Media | Pago |
| OFAC (público) | Sanções EUA | Gratuito, limitado |
| UN Sanctions | Sanções ONU | Gratuito |
| Mock | Desenvolvimento | Retorna resultados fixos |

### Regras

- Timeout máximo de 5 segundos por chamada.
- Retry com backoff exponencial (máx. 3 tentativas).
- Circuit breaker após 5 falhas consecutivas.
- Resultado cacheado por 24h por documento + lista.

## SISCOAF

### Porta

```java
public interface SiscoafProviderPort {
    SiscoafResponse submit(SiscoafCommunication communication);
    SiscoafStatus checkStatus(String protocolNumber);
}
```

### Regras

- Comunicação em XML conforme layout COAF.
- Assinatura digital obrigatória (certificado A1/A3).
- Protocolo retornado deve ser persistido.
- Retry apenas em erros 5xx ou timeout.
- Log de auditoria de toda comunicação enviada.

## KYC

### Porta

```java
public interface KycProviderPort {
    KycValidationResult validateDocument(String document, String type);
    KycValidationResult validateBiometric(byte[] image, String document);
}
```

### Provedores previstos

| Provedor | Uso |
|----------|-----|
| Serpro | Validação de CPF/CNPJ |
| Idwall / Unico | Biometria facial |
| Mock | Desenvolvimento |

## Configuração

```yaml
integrations:
  screening:
    provider: ${SCREENING_PROVIDER:mock}
    timeout-ms: 5000
  siscoaf:
    provider: ${SISCOAF_PROVIDER:mock}
    certificate-path: ${SISCOAF_CERT_PATH:}
  kyc:
    provider: ${KYC_PROVIDER:mock}
```

## Observações para manutenção

- Nunca chamar provedor externo direto do serviço de domínio.
- Sempre passar pela porta.
- Em ambiente de teste, usar mock.
- Monitorar latência e taxa de erro por provedor.
