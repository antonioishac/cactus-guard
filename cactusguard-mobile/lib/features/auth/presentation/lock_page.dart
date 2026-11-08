import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/auth_models.dart';
import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/widgets/logo.dart';

/// Tela de bloqueio: pede biometria ao abrir; fallback "Entrar com usuário e senha" encerra a sessão.
/// Não exibe nenhum dado de negócio (nem o nome do usuário).
class LockPage extends ConsumerStatefulWidget {
  const LockPage({super.key});

  @override
  ConsumerState<LockPage> createState() => _LockPageState();
}

class _LockPageState extends ConsumerState<LockPage> {
  bool _busy = false;
  bool _failed = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _unlock());
  }

  Future<void> _unlock() async {
    if (_busy) return;
    setState(() => _busy = true);
    final ok = await ref.read(appLockProvider).unlock();
    if (mounted) {
      setState(() {
        _busy = false;
        _failed = !ok;
      });
    }
  }

  Future<void> _usePassword() => ref.read(authControllerProvider).logout(ClearReason.locked);

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return PopScope(
      canPop: false,
      child: Scaffold(
        backgroundColor: AppColors.sidebar,
        body: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const CgLogo(size: 40, onDark: true),
                  const SizedBox(height: 32),
                  const Icon(Icons.lock_outline, size: 48, color: AppColors.accent),
                  const SizedBox(height: 16),
                  Text('CactusGuard bloqueado', style: t.textTheme.titleLarge?.copyWith(color: Colors.white)),
                  const SizedBox(height: 8),
                  Text(
                    _failed ? 'Não foi possível confirmar sua identidade.' : 'Confirme sua identidade para continuar.',
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: Colors.white70),
                  ),
                  const SizedBox(height: 32),
                  FilledButton.icon(
                    key: const Key('lock-unlock'),
                    style: FilledButton.styleFrom(backgroundColor: AppColors.accent, foregroundColor: AppColors.sidebar),
                    onPressed: _busy ? null : _unlock,
                    icon: const Icon(Icons.fingerprint),
                    label: const Text('Desbloquear com biometria'),
                  ),
                  const SizedBox(height: 12),
                  TextButton(
                    key: const Key('lock-password'),
                    onPressed: _busy ? null : _usePassword,
                    child: const Text('Entrar com usuário e senha', style: TextStyle(color: Colors.white)),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
