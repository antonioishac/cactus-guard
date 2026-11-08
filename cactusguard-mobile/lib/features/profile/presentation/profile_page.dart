import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/providers.dart';
import '../../../core/widgets/app_scaffold.dart';
import '../../../core/widgets/feedback.dart';

/// Perfil: dados do usuário, papéis, biometria e sair.
class ProfilePage extends ConsumerStatefulWidget {
  const ProfilePage({super.key});

  @override
  ConsumerState<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends ConsumerState<ProfilePage> {
  bool? _available;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    ref.read(appLockProvider).isAvailable().then((v) {
      if (mounted) setState(() => _available = v);
    });
  }

  Future<void> _toggleBiometric(bool value) async {
    setState(() => _busy = true);
    final ok = await ref.read(appLockProvider).setEnabled(value);
    if (!mounted) return;
    setState(() => _busy = false);
    if (!ok) AppSnack.error(context, 'Não foi possível ativar a biometria.');
    if (ok) AppSnack.success(context, value ? 'Desbloqueio por biometria ativado' : 'Biometria desativada');
  }

  Future<void> _logout() async {
    final confirm = await showConfirmDialog(
      context,
      title: 'Sair do CactusGuard',
      message: 'Deseja encerrar a sessão neste aparelho?',
      confirmLabel: 'Sair',
    );
    if (confirm) await ref.read(authControllerProvider).logout();
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final user = ref.watch(sessionProvider).user;
    final bioEnabled = ref.watch(biometricEnabledProvider);
    final config = ref.watch(appConfigProvider);
    return AppScaffold(
      title: 'Perfil',
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: ListTile(
              leading: CircleAvatar(
                backgroundColor: t.colorScheme.primaryContainer,
                child: Text(_initials(user?.name ?? '?'), style: TextStyle(color: t.colorScheme.onPrimaryContainer)),
              ),
              title: Text(user?.name ?? '—'),
              subtitle: Text(user?.username ?? ''),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Papéis', style: t.textTheme.titleSmall),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [for (final r in user?.roles ?? const []) Chip(label: Text(r.label))],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Column(
              children: [
                SwitchListTile(
                  key: const Key('profile-biometric'),
                  secondary: const Icon(Icons.fingerprint),
                  title: const Text('Desbloqueio por biometria'),
                  subtitle: Text(_available == false
                      ? 'Indisponível neste aparelho'
                      : 'Pedido ao voltar ao app após ${config.biometricLockAfter.inSeconds} s em segundo plano'),
                  value: bioEnabled,
                  onChanged: (_available ?? false) && !_busy ? _toggleBiometric : null,
                ),
                ListTile(
                  leading: const Icon(Icons.timer_outlined),
                  title: const Text('Sessão por inatividade'),
                  subtitle: Text('Encerrada após ${config.idleTimeout.inMinutes} min sem uso'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          OutlinedButton.icon(
            key: const Key('profile-logout'),
            style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(48), foregroundColor: t.colorScheme.error),
            onPressed: _logout,
            icon: const Icon(Icons.logout),
            label: const Text('Sair'),
          ),
          const SizedBox(height: 16),
          Center(child: Text('Versão ${config.appVersion}${config.useMock ? ' · modo demonstração' : ''}', style: t.textTheme.bodySmall)),
        ],
      ),
    );
  }

  static String _initials(String name) {
    final parts = name.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
    if (parts.isEmpty) return '?';
    return (parts.first[0] + (parts.length > 1 ? parts[1][0] : '')).toUpperCase();
  }
}
