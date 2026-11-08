import 'package:cactusguard_mobile/core/utils/format.dart';
import 'package:cactusguard_mobile/core/utils/logger.dart';
import 'package:cactusguard_mobile/core/utils/mask.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

void main() {
  setUpAll(() => initializeDateFormatting('pt_BR'));

  group('mascaramento de documento', () {
    test('CPF e CNPJ crus são mascarados', () {
      expect(Mask.document('12345678909'), '***.456.789-**');
      expect(Mask.document('123.456.789-09'), '***.456.789-**');
      expect(Mask.document('12345678000195'), '**.345.678/0001-**');
      expect(Mask.document('12.345.678/0001-95'), '**.345.678/0001-**');
    });

    test('já mascarado pelo backend é mantido; outros viram asteriscos', () {
      expect(Mask.document('***.456.789-**'), '***.456.789-**');
      expect(Mask.document('12'), '****');
      expect(Mask.document('123456'), '******');
      expect(Mask.document(null), '—');
      expect(Mask.document('  '), '—');
    });
  });

  group('formatação pt-BR', () {
    test('moeda BRL', () {
      expect(Fmt.brl(1234.5).replaceAll(' ', ' '), r'R$ 1.234,50');
      expect(Fmt.brl(null), '—');
    });

    test('data/hora em America/Sao_Paulo (UTC-3)', () {
      expect(Fmt.dateTime('2026-11-08T13:05:00Z'), '08/11/2026 10:05');
      expect(Fmt.dateTime('2026-11-08T02:00:00Z'), '07/11/2026 23:00');
      expect(Fmt.dateTime('2026-11-08T10:00:00-03:00'), '08/11/2026 10:00');
      expect(Fmt.dateTime('lixo'), '—');
    });

    test('data pura não volta um dia', () {
      expect(Fmt.date('2026-11-08'), '08/11/2026');
      expect(Fmt.date('2026-11-08T01:00:00Z'), '07/11/2026');
    });
  });

  test('logger mascara documento, JWT e Bearer', () {
    final out = AppLogger.redact('cpf 123.456.789-09 cnpj 12345678000195 Bearer abc.def eyJhbGciOi.eyJzdWIi.sig');
    expect(out, isNot(contains('123.456.789-09')));
    expect(out, isNot(contains('12345678000195')));
    expect(out, isNot(contains('eyJhbGciOi')));
    expect(out, contains('<cpf>'));
    expect(out, contains('<cnpj>'));
    expect(out, contains('Bearer <token>'));
  });

  test('labels pt-BR', () {
    expect(Labels.severity('CRITICAL'), 'Crítica');
    expect(Labels.status('PENDING_APPROVAL'), 'Aguardando aprovação');
    expect(Labels.decision('ARCHIVE'), 'Arquivar');
  });
}
