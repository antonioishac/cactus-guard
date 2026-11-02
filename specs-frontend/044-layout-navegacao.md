# Spec 044 — Layout e Navegação

## Objetivo

Implementar o layout principal do CactusGuard Web com menu responsivo.

## Comportamento

| Viewport | Menu |
|----------|------|
| Desktop (≥1024px) | Sidebar expandida visível |
| Tablet (768-1023px) | Sidebar colapsada (ícones) ou hambúrguer |
| Mobile (<768px) | Hambúrguer, menu overlay |

## Componentes

### AppLayout
- TopBar com hambúrguer, logo e usuário
- Sidebar (desktop) ou Drawer (mobile)
- Content area

### Sidebar
- Logo CactusGuard no topo
- Itens de navegação com ícones
- Item ativo destacado com teal
- Footer com versão

### TopBar
- Botão hambúrguer (mobile/tablet)
- Logo (quando sidebar oculta)
- Notificações
- Avatar do usuário + dropdown

## Itens de Menu

1. Dashboard
2. Partes
3. Alertas
4. Casos
5. Screening
6. KYC
7. Relatórios
8. Monitoramento
9. Configurações

## Cores

```css
--sidebar-bg: #0d3d36;
--accent: #00c9a7;
--accent-hover: #00b396;
--text-primary: #0a0a0a;
--text-secondary: #737373;
--border: #e5e5e5;
--bg: #ffffff;
```
