import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/mock/fake_data.dart';
import '../../../core/providers.dart';
import '../../../core/push/push_service.dart';
import '../../../core/widgets/app_scaffold.dart';
import '../../../core/widgets/feedback.dart';
import '../../../core/widgets/under_construction.dart';

/// Placeholder — trilha "Alertas/Notificações" implementa a lista e o indicador de não lidas.
/// Com `PUSH_MODE=local` exibe um botão de dev para simular push (payload só `{type,id}`).
class NotificationsPage extends ConsumerWidget {
  const NotificationsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final push = ref.watch(pushServiceProvider);
    return AppScaffold(
      title: 'Notificações',
      body: const UnderConstruction(feature: 'A central de notificações'),
      floatingActionButton: push is LocalSimulatedPushService
          ? FloatingActionButton.extended(
              onPressed: () async {
                final ok = await push.simulate({'type': 'ALERT', 'id': FakeData.alerts.first['id']});
                if (context.mounted) {
                  ok ? AppSnack.success(context, 'Push simulado enviado') : AppSnack.error(context, 'Payload rejeitado');
                }
              },
              icon: const Icon(Icons.send),
              label: const Text('Simular push'),
            )
          : null,
    );
  }
}
