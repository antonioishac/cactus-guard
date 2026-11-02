# Spec 030 — Frontend Web: Comunicação SISCOAF

## Objetivo
Acompanhamento e disparo controlado de comunicações ao COAF.

## Funcionalidades
- Listagem de comunicações (COS, COE, automáticas, declaração negativa) com filtros.
- Detalhe: status, protocolo, data, payload (somente leitura).
- Ação de envio manual quando o backend permitir e o usuário tiver permissão.
- Indicadores de erro e pendência.

## Integração
- Consome Communication do backend.
- O front não monta nem valida o payload regulatório.

## Regras de tela e UX
- Destaque visual para pendentes e erros.
- Confirmação obrigatória antes de envio.
- Feedback de sucesso/erro após envio.
- Campos somente leitura do payload.
