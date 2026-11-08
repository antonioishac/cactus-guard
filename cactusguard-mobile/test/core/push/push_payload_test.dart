import 'package:cactusguard_mobile/core/push/deep_link.dart';
import 'package:cactusguard_mobile/core/push/push_payload.dart';
import 'package:cactusguard_mobile/core/utils/logger.dart';
import 'package:flutter_test/flutter_test.dart';

const id = '3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b';

void main() {
  final logs = <String>[];
  setUp(() {
    logs.clear();
    AppLogger.sink = logs.add;
  });

  group('payload de push', () {
    test('aceita somente {type, id}', () {
      final p = PushPayload.tryParse({'type': 'ALERT', 'id': id});
      expect(p, const PushPayload(type: PushType.alert, id: id));
      expect(p!.location, '/alerts/$id');
      expect(PushPayload.tryParse({'type': 'CASE', 'id': id})!.location, '/cases/$id');
      expect(PushPayload.tryParse({'type': 'SLA', 'id': id})!.location, '/cases/$id');
    });

    test('rejeita payload com campos extras de PII e não registra os valores', () {
      final p = PushPayload.tryParse({
        'type': 'ALERT',
        'id': id,
        'partyName': 'Fulano de Tal',
        'document': '123.456.789-09',
      });
      expect(p, isNull);
      expect(logs.join(), isNot(contains('Fulano')));
      expect(logs.join(), isNot(contains('123.456.789-09')));
      expect(PushPayload.tryParse({'type': 'ALERT', 'id': id, 'amount': 50000}), isNull);
    });

    test('tolera chaves de transporte do FCM/APNs', () {
      expect(PushPayload.tryParse({'type': 'ALERT', 'id': id, 'google.message_id': 'x', 'from': '123', 'collapse_key': 'k'}), isNotNull);
    });

    test('rejeita tipo desconhecido, id inválido ou ausente', () {
      expect(PushPayload.tryParse({'type': 'KYC', 'id': id}), isNull);
      expect(PushPayload.tryParse({'type': 'ALERT', 'id': '../../etc'}), isNull);
      expect(PushPayload.tryParse({'type': 'ALERT'}), isNull);
      expect(PushPayload.tryParse({'id': id}), isNull);
      expect(PushPayload.tryParse({'type': 1, 'id': id}), isNull);
    });

    test('texto da notificação é genérico', () {
      for (final t in PushType.values) {
        expect(t.genericText, isNot(contains(RegExp(r'\d'))));
      }
      expect(PushType.alert.genericText, 'Novo alerta crítico para análise');
    });

    test('deep link equivalente', () {
      expect(const PushPayload(type: PushType.alert, id: id).deepLink.toString(), 'cactusguard://alerts/$id');
    });
  });

  group('deep link → rota', () {
    test('cactusguard://alerts/{id} e cases/{id}', () {
      expect(DeepLink.toLocation(Uri.parse('cactusguard://alerts/$id')), '/alerts/$id');
      expect(DeepLink.toLocation(Uri.parse('cactusguard://cases/${id.toUpperCase()}')), '/cases/$id');
      expect(DeepLink.toLocation(Uri.parse('/alerts/$id')), '/alerts/$id');
    });

    test('rejeita host desconhecido, id inválido, caminhos extras e outros esquemas', () {
      expect(DeepLink.toLocation(Uri.parse('cactusguard://users/$id')), isNull);
      expect(DeepLink.toLocation(Uri.parse('cactusguard://alerts/abc')), isNull);
      expect(DeepLink.toLocation(Uri.parse('cactusguard://alerts/$id/extra')), isNull);
      expect(DeepLink.toLocation(Uri.parse('https://evil.com/alerts/$id')), isNull);
    });
  });
}
