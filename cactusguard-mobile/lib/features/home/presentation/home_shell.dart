import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/auth/auth_models.dart';
import '../../../core/providers.dart';
import '../../../core/router/app_router.dart';

/// Item da bottom navigation. [roles] vazio = todos os papéis.
class NavItem {
  const NavItem(this.branch, this.label, this.icon, this.selectedIcon, [this.roles = const []]);
  final int branch;
  final String label;
  final IconData icon;
  final IconData selectedIcon;
  final List<Role> roles;
}

const navItems = [
  NavItem(ShellBranch.alerts, 'Alertas', Icons.notification_important_outlined, Icons.notification_important),
  NavItem(ShellBranch.cases, 'Cases', Icons.folder_open_outlined, Icons.folder),
  NavItem(ShellBranch.approvals, 'Aprovações', Icons.fact_check_outlined, Icons.fact_check, [Role.supervisor]),
  NavItem(ShellBranch.notifications, 'Notificações', Icons.notifications_none, Icons.notifications),
  NavItem(ShellBranch.profile, 'Perfil', Icons.person_outline, Icons.person),
];

/// Itens visíveis para os papéis (Aprovações só SUPERVISOR).
List<NavItem> visibleNavItems(List<Role> roles) =>
    navItems.where((i) => i.roles.isEmpty || i.roles.any(roles.contains)).toList(growable: false);

/// Home: shell com bottom navigation por papel; cada aba mantém sua pilha (StatefulShellRoute).
class HomeShell extends ConsumerWidget {
  const HomeShell({super.key, required this.navigationShell});
  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final items = visibleNavItems(ref.watch(sessionProvider).roles);
    final selected = items.indexWhere((i) => i.branch == navigationShell.currentIndex);
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: selected < 0 ? 0 : selected,
        onDestinationSelected: (i) {
          final branch = items[i].branch;
          navigationShell.goBranch(branch, initialLocation: branch == navigationShell.currentIndex);
        },
        destinations: [
          for (final i in items)
            NavigationDestination(icon: Icon(i.icon), selectedIcon: Icon(i.selectedIcon), label: i.label),
        ],
      ),
    );
  }
}
