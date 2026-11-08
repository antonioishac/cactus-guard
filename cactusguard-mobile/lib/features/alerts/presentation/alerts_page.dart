import 'package:flutter/material.dart';

import '../../../core/push/push_payload.dart';
import '../../../core/widgets/app_scaffold.dart';
import '../../../core/widgets/states.dart';
import '../../../core/widgets/under_construction.dart';

/// Placeholder — trilha "Alertas/Notificações" implementa lista e filtros (spec 032).
class AlertsPage extends StatelessWidget {
  const AlertsPage({super.key});

  @override
  Widget build(BuildContext context) =>
      const AppScaffold(title: 'Alertas', body: UnderConstruction(feature: 'A fila de alertas'));
}

/// Placeholder do detalhe (destino do deep link `cactusguard://alerts/{id}`).
class AlertDetailPage extends StatelessWidget {
  const AlertDetailPage({super.key, required this.id});
  final String id;

  @override
  Widget build(BuildContext context) => AppScaffold(
        title: 'Alerta',
        body: PushPayload.isValidId(id)
            ? UnderConstruction(key: Key('alert-detail-$id'), feature: 'O detalhe do alerta')
            : const EmptyState(icon: Icons.search_off, title: 'Alerta não encontrado'),
      );
}
