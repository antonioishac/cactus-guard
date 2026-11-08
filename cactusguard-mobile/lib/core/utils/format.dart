import 'package:intl/intl.dart';

/// Formatação pt-BR (espelha `cactusguard-web/src/lib/format.ts`). Nada aqui registra valores em log.
///
/// Fuso: America/Sao_Paulo. Desde 2019 (Decreto 9.772) não há horário de verão, então o fuso é UTC-3
/// fixo; usar offset fixo evita carregar a base tz inteira no app.
abstract final class Fmt {
  static const empty = '—';
  static const saoPauloOffset = Duration(hours: -3);

  static final _brl = NumberFormat.currency(locale: 'pt_BR', symbol: r'R$', decimalDigits: 2);
  static final _num = NumberFormat.decimalPattern('pt_BR');

  static String brl(num? value) => value == null ? empty : _brl.format(value);

  static String number(num? value) => value == null ? empty : _num.format(value);

  /// Converte um instante para o "relógio" de São Paulo (DateTime UTC deslocado).
  static DateTime toSaoPaulo(DateTime instant) => instant.toUtc().add(saoPauloOffset);

  /// `dd/MM/yyyy HH:mm` em America/Sao_Paulo. Aceita ISO-8601 com offset ou `Z`.
  static String dateTime(String? iso) {
    final d = _parse(iso);
    return d == null ? empty : DateFormat('dd/MM/yyyy HH:mm', 'pt_BR').format(toSaoPaulo(d));
  }

  /// Data pura `yyyy-MM-dd` (sem fuso) ou instante ISO (convertido para São Paulo).
  static String date(String? value) {
    if (value == null || value.isEmpty) return empty;
    if (RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(value)) {
      final d = DateTime.tryParse('${value}T00:00:00Z');
      return d == null ? empty : DateFormat('dd/MM/yyyy', 'pt_BR').format(d);
    }
    final d = _parse(value);
    return d == null ? empty : DateFormat('dd/MM/yyyy', 'pt_BR').format(toSaoPaulo(d));
  }

  static DateTime? _parse(String? iso) {
    if (iso == null || iso.isEmpty) return null;
    final d = DateTime.tryParse(iso);
    if (d == null) return null;
    // ISO sem offset é tratado como UTC (o backend serializa Instant com Z).
    final hasZone = RegExp(r'(Z|[+-]\d{2}:?\d{2})$').hasMatch(iso);
    return hasZone ? d : DateTime.utc(d.year, d.month, d.day, d.hour, d.minute, d.second);
  }
}
