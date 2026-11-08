import 'package:flutter/foundation.dart';

/// Logger do app. Regras (spec 024 / LGPD):
/// - Em release/profile **nada** é impresso.
/// - Nunca recebe corpo de requisição/resposta, headers ou tokens; mesmo assim, toda mensagem passa
///   por [redact] (CPF/CNPJ, JWT, Bearer) como defesa em profundidade.
abstract final class AppLogger {
  /// Permite silenciar/capturar em testes.
  static void Function(String line)? sink;

  static bool get enabled => kDebugMode;

  static void debug(String message) => _emit('D', message);
  static void info(String message) => _emit('I', message);
  static void warn(String message) => _emit('W', message);

  /// Registra apenas o tipo do erro (a mensagem pode carregar dados do servidor).
  static void error(String message, [Object? error]) =>
      _emit('E', error == null ? message : '$message (${error.runtimeType})');

  static void _emit(String level, String message) {
    if (!enabled) return;
    final line = '[CG/$level] ${redact(message)}';
    (sink ?? debugPrint)(line);
  }

  static final _jwt = RegExp(r'eyJ[\w-]+\.[\w-]+\.[\w-]+');
  static final _bearer = RegExp(r'Bearer\s+\S+', caseSensitive: false);
  static final _cnpj = RegExp(r'\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2}');
  static final _cpf = RegExp(r'\d{3}\.?\d{3}\.?\d{3}-?\d{2}');

  static String redact(String input) => input
      .replaceAll(_jwt, '<jwt>')
      .replaceAll(_bearer, 'Bearer <token>')
      .replaceAll(_cnpj, '<cnpj>')
      .replaceAll(_cpf, '<cpf>');
}
