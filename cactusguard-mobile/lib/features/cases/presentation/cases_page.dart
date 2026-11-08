import 'package:flutter/material.dart';

import '../../../core/push/push_payload.dart';
import '../../../core/widgets/app_scaffold.dart';
import '../../../core/widgets/states.dart';
import '../../../core/widgets/under_construction.dart';

/// Placeholder — trilha "Cases/Aprovações" implementa lista e detalhe (specs 012/033).
class CasesPage extends StatelessWidget {
  const CasesPage({super.key});

  @override
  Widget build(BuildContext context) =>
      const AppScaffold(title: 'Cases', body: UnderConstruction(feature: 'A lista de cases'));
}

/// Placeholder do detalhe (destino do deep link `cactusguard://cases/{id}`).
class CaseDetailPage extends StatelessWidget {
  const CaseDetailPage({super.key, required this.id});
  final String id;

  @override
  Widget build(BuildContext context) => AppScaffold(
        title: 'Case',
        body: PushPayload.isValidId(id)
            ? UnderConstruction(key: Key('case-detail-$id'), feature: 'O detalhe do case')
            : const EmptyState(icon: Icons.search_off, title: 'Case não encontrado'),
      );
}
