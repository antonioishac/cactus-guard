import 'package:flutter/foundation.dart';

import '../utils/logger.dart';

/// Tipos de push aceitos. `SLA` se refere a prazo de **case** (o id é de case).
enum PushType {
  alert('ALERT', 'Novo alerta crítico para análise'),
  kase('CASE', 'Atualização em case sob sua responsabilidade'),
  sla('SLA', 'Prazo de análise próximo do vencimento');

  const PushType(this.code, this.genericText);
  final String code;

  /// Texto **genérico** exibido na notificação — nunca nome, documento ou valor (tipping-off, spec 024).
  final String genericText;
}

/// Payload de push. Contrato: **somente** `{type: ALERT|CASE|SLA, id: uuid}`; o app busca o detalhe
/// autenticado. Qualquer campo extra (possível PII) faz o payload ser rejeitado.
@immutable
class PushPayload {
  const PushPayload({required this.type, required this.id});

  final PushType type;
  final String id;

  static final _uuid = RegExp(r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$');

  /// Chaves de controle que o FCM/APNs injetam no `data` e que não são dados do app.
  static bool _isTransportKey(String k) =>
      k.startsWith('google.') ||
      k.startsWith('gcm.') ||
      k == 'from' ||
      k == 'collapse_key' ||
      k == 'aps';

  static bool isValidId(String id) => _uuid.hasMatch(id);

  /// Retorna `null` (e registra só o motivo, sem valores) para payload inválido ou com campos extras.
  static PushPayload? tryParse(Map<String, dynamic> data) {
    final extra = data.keys.where((k) => k != 'type' && k != 'id' && !_isTransportKey(k)).toList();
    if (extra.isNotEmpty) {
      AppLogger.warn('Push rejeitado: ${extra.length} campo(s) não permitido(s) no payload');
      return null;
    }
    final rawType = data['type'];
    final id = data['id'];
    if (rawType is! String || id is! String || !isValidId(id)) {
      AppLogger.warn('Push rejeitado: type/id ausente ou inválido');
      return null;
    }
    final type = PushType.values.where((t) => t.code == rawType.toUpperCase()).firstOrNull;
    if (type == null) {
      AppLogger.warn('Push rejeitado: tipo desconhecido');
      return null;
    }
    return PushPayload(type: type, id: id.toLowerCase());
  }

  /// Rota interna do go_router para o detalhe.
  String get location => switch (type) {
        PushType.alert => '/alerts/$id',
        PushType.kase || PushType.sla => '/cases/$id',
      };

  /// Deep link equivalente (`cactusguard://alerts/{id}` ou `cactusguard://cases/{id}`).
  Uri get deepLink => Uri.parse('cactusguard:/$location');

  Map<String, String> toData() => {'type': type.code, 'id': id};

  @override
  bool operator ==(Object other) => other is PushPayload && other.type == type && other.id == id;

  @override
  int get hashCode => Object.hash(type, id);
}
