# CactusGuard

Sistema de Prevenção à Lavagem de Dinheiro (PLD/AML) para bancos, instituições financeiras e fintechs.

## Visão Geral

O CactusGuard é uma plataforma completa de compliance PLD que oferece:

- **KYC/KYE/KYP** — Conhecimento do cliente, empregado e parceiro
- **Screening** — Verificação contra listas de sanções, PEP e watchlists
- **Monitoramento Transacional** — Motor de regras em tempo real
- **Gestão de Casos e Alertas** — Workflow completo de investigação
- **Relatórios** — Exportação PDF e Excel
- **SISCOAF** — Integração com o sistema de comunicação de operações atípicas

## Stack

### Backend
| Tecnologia | Versão |
|------------|--------|
| Java | 25 |
| Spring Boot | 4.x |
| PostgreSQL | 15+ |
| Redis | 7+ |
| Kafka | 3.x (planejado — ver `specs/README.md`) |

### Frontend
| Tecnologia | Versão |
|------------|--------|
| React | 18 |
| Vite | 5 |
| Tailwind CSS | 3 |
| Zustand | 5 |
| Recharts | 2 |

## Estrutura do Projeto

```
cactus-guard/
├── cactusguard-backend/     # API Spring Boot
│   ├── src/main/java/br/com/cactus/guard/
│   └── src/main/resources/
├── cactusguard-web/         # Frontend React
│   └── src/
├── specs/                   # Especificações (índice e status: specs/README.md)
├── specs-frontend/          # Especificações de design do frontend (043-045)
└── CLAUDE.md                # Instruções para Claude Code
```

## Módulos

| Módulo | Descrição |
|--------|-----------|
| Autenticação | JWT + Refresh Token, roles e permissões |
| Partes | Cadastro de clientes (PF/PJ), endereços, relacionamentos |
| KYC | Documentos, checklist, aprovação |
| Screening | Integração com provedores externos (Dow Jones, etc.) |
| Monitoramento | Motor de regras (PEP, sanções, valor alto, velocidade) |
| Alertas | Geração automática, severidade, SLA |
| Casos | Investigação, decisão, trilha de auditoria |
| Relatórios | Dashboard, exportação PDF/Excel |
| SISCOAF | Comunicação de operações atípicas |

## Como Executar

### Backend
```bash
cd cactusguard-backend
JAVA_HOME=/c/Java/jdk-25.0.2 mvn spring-boot:run   # ajuste JAVA_HOME para seu JDK 25
```

### Frontend
```bash
cd cactusguard-web
npm install
npm run dev
```

## Configuração

### Banco de Dados
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cactusguard
    username: postgres
    password: postgres
```

### Redis
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

## Design

- **Cores**: Teal #00c9a7 (accent), sidebar #0d3d36
- **Menu**: Hambúrguer responsivo + sidebar expandida no desktop
- **Tema**: Light com sidebar escura
- **Gráficos**: Recharts (linha, donut)

## Licença

Proprietário — Todos os direitos reservados.
