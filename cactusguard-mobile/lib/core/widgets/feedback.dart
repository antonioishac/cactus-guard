import 'package:flutter/material.dart';

import '../api/api_error.dart';
import '../theme/app_colors.dart';

/// SnackBars padronizados de sucesso/erro.
abstract final class AppSnack {
  static void success(BuildContext context, String message) => _show(context, message, AppColors.success, Icons.check_circle);

  /// Aceita mensagem ou erro (converte para [ApiError.userMessage]).
  static void error(BuildContext context, Object messageOrError) {
    final msg = messageOrError is String ? messageOrError : ApiError.from(messageOrError).userMessage;
    _show(context, msg, AppColors.danger, Icons.error);
  }

  static void _show(BuildContext context, String message, Color color, IconData icon) {
    final m = ScaffoldMessenger.maybeOf(context);
    if (m == null) return;
    m
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(
        backgroundColor: color,
        content: Row(children: [
          Icon(icon, color: Colors.white, size: 20),
          const SizedBox(width: 10),
          Expanded(child: Text(message, style: const TextStyle(color: Colors.white))),
        ]),
      ));
  }
}

/// Confirmação simples (sem justificativa). Para ações regulatórias use `showJustificationDialog`.
Future<bool> showConfirmDialog(
  BuildContext context, {
  required String title,
  required String message,
  String confirmLabel = 'Confirmar',
  bool destructive = false,
}) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(title),
      content: Text(message),
      actions: [
        TextButton(onPressed: () => Navigator.of(ctx).pop(false), child: const Text('Cancelar')),
        FilledButton(
          style: destructive ? FilledButton.styleFrom(backgroundColor: AppColors.danger, minimumSize: const Size(88, 40)) : FilledButton.styleFrom(minimumSize: const Size(88, 40)),
          onPressed: () => Navigator.of(ctx).pop(true),
          child: Text(confirmLabel),
        ),
      ],
    ),
  );
  return ok ?? false;
}
