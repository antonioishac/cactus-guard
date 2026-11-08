import 'package:flutter/material.dart';

import '../../../core/widgets/app_scaffold.dart';
import '../../../core/widgets/under_construction.dart';

/// Placeholder — fila de aprovação (quatro olhos), só SUPERVISOR. Trilha "Cases/Aprovações".
class ApprovalsPage extends StatelessWidget {
  const ApprovalsPage({super.key});

  @override
  Widget build(BuildContext context) =>
      const AppScaffold(title: 'Aprovações', body: UnderConstruction(feature: 'A fila de aprovações'));
}
