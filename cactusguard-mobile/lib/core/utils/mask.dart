/// Mascaramento e rótulos (espelha `cactusguard-web/src/lib/format.ts` e `document.ts`).
/// O backend já devolve PII mascarada; aqui é defesa em profundidade para dígitos crus.
abstract final class Mask {
  static const empty = '—';

  static String onlyDigits(String v) => v.replaceAll(RegExp(r'\D'), '');

  /// Se já vier mascarado (`*` ou `•`) devolve como está; CPF → `***.456.789-**`, CNPJ → `**.345.678/0001-**`.
  static String document(String? value) {
    if (value == null || value.trim().isEmpty) return empty;
    if (RegExp(r'[*•]').hasMatch(value)) return value;
    final d = onlyDigits(value);
    if (d.length == 11) return '***.${d.substring(3, 6)}.${d.substring(6, 9)}-**';
    if (d.length == 14) return '**.${d.substring(2, 5)}.${d.substring(5, 8)}/${d.substring(8, 12)}-**';
    return '*' * (d.length < 4 ? 4 : d.length);
  }
}

abstract final class Labels {
  static const _status = {
    'OPEN': 'Aberto',
    'IN_ANALYSIS': 'Em análise',
    'ESCALATED': 'Escalado',
    'FALSE_POSITIVE': 'Falso positivo',
    'CLOSED': 'Encerrado',
    'PENDING_APPROVAL': 'Aguardando aprovação',
    'COMMUNICATE': 'Comunicar (aprovado)',
    'ARCHIVED': 'Arquivado',
    'ACTIVE': 'Ativo',
  };
  static const _severity = {'LOW': 'Baixa', 'MEDIUM': 'Média', 'HIGH': 'Alta', 'CRITICAL': 'Crítica'};
  static const _decision = {'COMMUNICATE': 'Comunicar', 'ARCHIVE': 'Arquivar'};

  static String status(String s) => _status[s] ?? s;
  static String severity(String s) => _severity[s] ?? s;
  static String decision(String s) => _decision[s] ?? s;
}
