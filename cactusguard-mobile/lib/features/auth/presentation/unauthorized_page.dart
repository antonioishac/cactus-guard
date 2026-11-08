import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../core/router/route_guard.dart';
import '../../../core/widgets/app_scaffold.dart';
import '../../../core/widgets/states.dart';

/// Acesso negado (papel insuficiente) ou rota inexistente.
class UnauthorizedPage extends StatelessWidget {
  const UnauthorizedPage({super.key, this.notFound = false});
  final bool notFound;

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      body: EmptyState(
        icon: notFound ? Icons.search_off : Icons.block,
        title: notFound ? 'Página não encontrada' : 'Acesso negado',
        message: notFound ? 'O endereço solicitado não existe.' : 'Seu perfil não tem permissão para esta área.',
        action: FilledButton(
          style: FilledButton.styleFrom(minimumSize: const Size(160, 44)),
          onPressed: () => context.go(Routes.home),
          child: const Text('Ir para o início'),
        ),
      ),
    );
  }
}
