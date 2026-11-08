import 'package:flutter/material.dart';

import '../utils/mask.dart';

/// Exibe CPF/CNPJ sempre mascarado. Revelação de PII é ação do backend (`POST /parties/{id}/reveal`
/// com motivo e auditoria) — este widget nunca desmascara.
class MaskedText extends StatelessWidget {
  const MaskedText(this.value, {super.key, this.style});

  final String? value;
  final TextStyle? style;

  @override
  Widget build(BuildContext context) {
    return Text(
      Mask.document(value),
      style: style ?? const TextStyle(fontFeatures: [FontFeature.tabularFigures()]),
      semanticsLabel: 'Documento mascarado',
    );
  }
}
