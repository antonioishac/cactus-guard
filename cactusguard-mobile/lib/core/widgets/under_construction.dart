import 'package:flutter/material.dart';

import 'states.dart';

/// Placeholder das telas de negócio desta fundação (serão implementadas pelas próximas trilhas).
class UnderConstruction extends StatelessWidget {
  const UnderConstruction({super.key, required this.feature});
  final String feature;

  @override
  Widget build(BuildContext context) => EmptyState(
        icon: Icons.construction_outlined,
        title: 'Em construção',
        message: '$feature estará disponível em breve.',
      );
}
