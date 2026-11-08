/// Dados fictícios do backend fake. Nomes de empresas/pessoas inventados e documentos já mascarados
/// (o backend real nunca envia PII completa em listagens).
abstract final class FakeData {
  static final DateTime _base = DateTime.utc(2026, 11, 3, 13);

  static String _iso(Duration offset) => _base.add(offset).toIso8601String();

  static final List<Map<String, dynamic>> alerts = List.generate(12, (i) {
    const severities = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
    const statuses = ['OPEN', 'IN_ANALYSIS', 'OPEN', 'ESCALATED'];
    const names = [
      'Comercial Fictícia Alfa Ltda',
      'Pessoa Exemplo Beta',
      'Distribuidora Gama Ficta S.A.',
      'Exemplo Delta Serviços ME',
    ];
    return {
      'id': 'a1e7c000-0000-4000-8000-${(i + 1).toString().padLeft(12, '0')}',
      'partyId': 'b0000000-0000-4000-8000-${(i % 4 + 1).toString().padLeft(12, '0')}',
      'partyName': names[i % 4],
      'partyDocument': i.isEven ? '***.456.789-**' : '**.345.678/0001-**',
      'type': 'TRANSACTIONAL',
      'severity': severities[i % 4],
      'status': statuses[i % 4],
      'title': 'Fracionamento de depósitos em espécie (fictício)',
      'description': 'Alerta gerado pelo backend fake.',
      'ruleCode': 'R-CC4001-${(i % 5) + 1}',
      'ruleVersion': 1,
      'assignedToId': null,
      'occurredAt': _iso(Duration(days: -i)),
      'dueDate': _iso(Duration(days: 45 - i)),
      'selectionDeadline': _iso(Duration(days: 45 - i)),
      'totalAmount': 12500.0 * (i + 1),
      'falsePositiveReason': null,
      'createdAt': _iso(Duration(days: -i, hours: 1)),
    };
  });

  static final List<Map<String, dynamic>> cases = List.generate(6, (i) {
    const statuses = ['OPEN', 'ESCALATED', 'PENDING_APPROVAL', 'OPEN', 'ARCHIVED', 'PENDING_APPROVAL'];
    return {
      'id': 'ca5e0000-0000-4000-8000-${(i + 1).toString().padLeft(12, '0')}',
      'caseNumber': 'CG-2026-${(i + 101).toString().padLeft(5, '0')}',
      'alertId': alerts[i]['id'],
      'partyId': alerts[i]['partyId'],
      'partyName': alerts[i]['partyName'],
      'status': statuses[i],
      'priority': alerts[i]['severity'],
      'assignedToId': null,
      'decision': statuses[i] == 'PENDING_APPROVAL' ? 'COMMUNICATE' : null,
      'decisionJustification': null,
      'slaDueDate': _iso(Duration(days: 40 - i * 7)),
      'escalationLevel': statuses[i] == 'ESCALATED' ? 1 : 0,
      'edd': i == 2,
      'createdAt': _iso(Duration(days: -i)),
    };
  });

  /// Notificações com texto genérico (sem nome/documento — evita tipping-off).
  static final List<Map<String, dynamic>> notifications = [
    for (var i = 0; i < 5; i++)
      {
        'id': 'd0000000-0000-4000-8000-${(i + 1).toString().padLeft(12, '0')}',
        'type': i.isEven ? 'ALERT' : 'SLA',
        'title': i.isEven ? 'Novo alerta crítico para análise' : 'Prazo de análise próximo do vencimento',
        'message': null,
        'read': i > 1,
        'entityType': i.isEven ? 'ALERT' : 'CASE',
        'entityId': i.isEven ? alerts[i]['id'] : cases[i % cases.length]['id'],
        'createdAt': _iso(Duration(hours: -i)),
      },
  ];

  /// `Page<T>` do Spring (0-based).
  static Map<String, dynamic> page(List<Map<String, dynamic>> all, Map<String, dynamic> query) {
    var items = all;
    for (final key in ['status', 'severity']) {
      final v = query[key];
      if (v is String && v.isNotEmpty) items = items.where((e) => e[key] == v).toList();
    }
    final page = int.tryParse('${query['page'] ?? 0}') ?? 0;
    final size = int.tryParse('${query['size'] ?? 20}') ?? 20;
    final start = (page * size).clamp(0, items.length);
    final end = (start + size).clamp(0, items.length);
    return {
      'content': items.sublist(start, end),
      'totalElements': items.length,
      'totalPages': size == 0 ? 0 : (items.length / size).ceil(),
      'number': page,
      'size': size,
    };
  }
}
