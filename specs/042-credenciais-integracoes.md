# Spec 042 — Obtenção de Credenciais para Integrações

## Objetivo

Documentar o processo para obter as credenciais necessárias para as integrações externas do CactusGuard.

---

## 1. Dow Jones (Screening)

### O que é
Provedor global de listas de PEP, sanções e adverse media.

### Como obter

1. Acessar [https://www.dowjones.com/professional/risk/](https://www.dowjones.com/professional/risk/)
2. Solicitar demonstração ou contato comercial
3. Negociar contrato (valores variam por volume)
4. Receber API key e documentação técnica

### Credenciais necessárias

| Variável | Descrição |
|----------|-----------|
| `SCREENING_API_KEY` | Bearer token fornecido pelo Dow Jones |
| `SCREENING_BASE_URL` | URL da API (fornecida no contrato) |

### Tempo estimado
2 a 4 semanas (negociação comercial + setup técnico)

### Custo
Sob consulta. Tipicamente cobrado por consulta ou pacote mensal.

---

## 2. COAF / SISCOAF

### O que é
Sistema de comunicação de operações suspeitas ao Conselho de Controle de Atividades Financeiras.

### Como obter

1. A instituição deve estar obrigada a comunicar ao COAF (bancos, fintechs, etc.)
2. Acessar [https://www.gov.br/coaf](https://www.gov.br/coaf)
3. Solicitar credenciamento no SISCOAF
4. Obter certificado digital A1 ou A3 (e-CNPJ)
5. Configurar o certificado no sistema

### Credenciais necessárias

| Variável | Descrição |
|----------|-----------|
| `SISCOAF_BASE_URL` | URL do ambiente (homologação ou produção) |
| `SISCOAF_CERT_PATH` | Caminho do certificado digital (.pfx ou .p12) |
| `SISCOAF_CERT_PASSWORD` | Senha do certificado |

### Tempo estimado
4 a 8 semanas (credenciamento + certificado)

### Custo
Certificado digital: R$ 200 a R$ 500/ano. SISCOAF em si não tem custo de uso.

### Ambientes

| Ambiente | URL |
|----------|-----|
| Homologação | Fornecida pelo COAF após credenciamento |
| Produção | Fornecida pelo COAF após homologação |

---

## 3. Serpro (KYC)

### O que é
Serviço de validação de CPF/CNPJ e biometria do governo federal.

### Como obter

1. Acessar [https://www.serpro.gov.br](https://www.serpro.gov.br)
2. Criar conta no portal de desenvolvedores
3. Solicitar acesso às APIs de consulta CPF/CNPJ
4. Para biometria, solicitar acesso específico
5. Receber token de autenticação

### Credenciais necessárias

| Variável | Descrição |
|----------|-----------|
| `KYC_API_KEY` | Token Bearer do Serpro |
| `KYC_BASE_URL` | URL da API (gateway.apiserpro.serpro.gov.br) |

### Tempo estimado
1 a 2 semanas

### Custo
Consulta CPF: aproximadamente R$ 0,10 a R$ 0,50 por consulta (varia por volume).

---

## Configuração no CactusGuard

Após obter as credenciais, configurar no ambiente:

```bash
export SCREENING_PROVIDER=dowjones
export SCREENING_API_KEY=sua-api-key
export SCREENING_BASE_URL=https://api.dowjones.com

export SISCOAF_PROVIDER=coaf
export SISCOAF_BASE_URL=https://siscoaf.coaf.gov.br
export SISCOAF_CERT_PATH=/path/to/certificate.pfx
export SISCOAF_CERT_PASSWORD=senha-do-certificado

export KYC_PROVIDER=serpro
export KYC_API_KEY=seu-token
export KYC_BASE_URL=https://gateway.apiserpro.serpro.gov.br
```

Ou no `application.yml` de produção (usando secrets do Kubernetes/Docker).

---

## Checklist de go-live

- [ ] Contrato Dow Jones assinado
- [ ] API key Dow Jones recebida e testada
- [ ] Credenciamento SISCOAF concluído
- [ ] Certificado digital A1/A3 obtido
- [ ] Certificado testado em homologação COAF
- [ ] Conta Serpro criada
- [ ] Token Serpro recebido e testado
- [ ] Variáveis de ambiente configuradas em produção
- [ ] Teste de ponta a ponta em homologação
- [ ] Monitoramento de erros de integração ativo

---

## Contatos úteis

| Provedor | Contato |
|----------|---------|
| Dow Jones | Via site comercial |
| COAF | coaf@fazenda.gov.br |
| Serpro | portaldesenvolvedor.serpro.gov.br |
