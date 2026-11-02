# Spec 027 — Frontend Web: Relatórios e Exportação

## Objetivo
Disponibilizar todos os relatórios do backend com exportação para PDF e Excel.

## Funcionalidades
- Catálogo de relatórios disponíveis (operacionais, efetividade, regulatórios, auditoria).
- Filtros por período, tipo, status, tenant.
- Disparo de geração (quando assíncrono).
- Download direto de PDF e Excel.
- Histórico de gerações com status e data.
- Visualização prévia quando o backend fornecer.

## Integração
- Consome endpoints de listagem, geração e download de relatórios.
- O front apenas solicita e baixa; não gera o arquivo.

## Regras de tela e UX
- Feedback de processamento (loading, pronto, erro, em fila).
- Permissões por tipo de relatório (botões condicionados).
- Mensagens claras de falha na geração.
- Download automático ou botão de download após conclusão.
