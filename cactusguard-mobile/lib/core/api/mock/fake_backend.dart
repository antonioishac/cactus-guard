import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

import 'fake_data.dart';

/// Backend fake em memória (`--dart-define=USE_MOCK=true` e testes). Implementa um [HttpClientAdapter]
/// do Dio: todo o pipeline real (interceptor JWT, refresh em fila, mapeamento de erro) é exercitado.
///
/// Dados 100% fictícios, sem PII real; documentos já chegam mascarados, como no backend.
/// Usuários: `analista`, `supervisor`, `admin`, `auditor` — senha [password].
class FakeBackend implements HttpClientAdapter {
  FakeBackend({
    this.latency = const Duration(milliseconds: 250),
    this.refreshLatency,
    DateTime Function()? clock,
  }) : _clock = clock ?? DateTime.now;

  static const password = 'cactus123';

  /// Atraso simulado de cada resposta.
  Duration latency;

  /// Atraso específico do refresh (testes de concorrência).
  Duration? refreshLatency;

  final DateTime Function() _clock;

  /// Validade do access token emitido (o backend real usa 30 min).
  Duration accessTtl = const Duration(minutes: 30);

  /// Quando true, todo refresh falha (simula refresh token revogado/expirado).
  bool failRefresh = false;

  int loginCalls = 0;
  int refreshCalls = 0;
  int logoutCalls = 0;
  final List<Map<String, dynamic>> registeredDevices = [];
  final List<String> requests = [];

  final Map<String, DateTime> _access = {};
  final Map<String, String> _refresh = {}; // refresh -> username
  final Map<String, String> _accessUser = {};
  int _seq = 0;

  static final Map<String, Map<String, dynamic>> users = {
    'analista': {
      'id': '0b6a1d0e-1111-4a8e-9f00-000000000001',
      'username': 'analista',
      'name': 'Ana Analista (fictícia)',
      'roles': ['ANALYST'],
    },
    'supervisor': {
      'id': '0b6a1d0e-2222-4a8e-9f00-000000000002',
      'username': 'supervisor',
      'name': 'Sérgio Supervisor (fictício)',
      'roles': ['ROLE_SUPERVISOR'],
    },
    'admin': {
      'id': '0b6a1d0e-3333-4a8e-9f00-000000000003',
      'username': 'admin',
      'name': 'Adm. Sistema (fictício)',
      'roles': ['ADMIN'],
    },
    'auditor': {
      'id': '0b6a1d0e-4444-4a8e-9f00-000000000004',
      'username': 'auditor',
      'name': 'Aurora Auditora (fictícia)',
      'roles': ['AUDITOR'],
    },
  };

  /// Invalida todos os access tokens (próximas chamadas tomam 401 e disparam refresh).
  void expireAccessTokens() => _access.updateAll((_, _) => DateTime.fromMillisecondsSinceEpoch(0));

  /// Revoga um refresh token específico.
  void revokeRefresh(String token) => _refresh.remove(token);

  bool isRefreshValid(String token) => _refresh.containsKey(token);

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final path = options.uri.path.replaceFirst(RegExp(r'^.*?/api/v1'), '');
    final method = options.method.toUpperCase();
    requests.add('$method $path');
    final isRefresh = path == '/auth/refresh';
    final delay = isRefresh ? (refreshLatency ?? latency) : latency;
    if (delay > Duration.zero) await Future<void>.delayed(delay);

    final body = _body(options.data);
    switch ((method, path)) {
      case ('POST', '/auth/login'):
        return _login(body);
      case ('POST', '/auth/refresh'):
        return _refreshToken(body);
      case ('POST', '/auth/logout'):
        logoutCalls++;
        _refresh.remove(body['refreshToken']);
        return _json(204, null);
    }

    final user = _authenticate(options);
    if (user == null) return _error(401, 'UNAUTHORIZED', 'Credenciais inválidas');

    final segments = path.split('/').where((s) => s.isNotEmpty).toList();
    switch ((method, segments)) {
      case ('POST', ['devices']):
        final token = body['token'];
        final platform = body['platform'];
        if (token is! String || token.isEmpty || (platform != 'ANDROID' && platform != 'IOS')) {
          return _error(400, 'VALIDATION_ERROR', 'Dados do aparelho inválidos');
        }
        registeredDevices.removeWhere((d) => d['token'] == token);
        registeredDevices.add({...body, 'user': user});
        return _json(201, {'token': token, 'platform': platform});
      case ('DELETE', ['devices', final token]):
        registeredDevices.removeWhere((d) => d['token'] == Uri.decodeComponent(token));
        return _json(204, null);
      case ('GET', ['alerts']):
        return _json(200, FakeData.page(FakeData.alerts, options.queryParameters));
      case ('GET', ['alerts', final id]):
        final a = FakeData.alerts.where((e) => e['id'] == id).firstOrNull;
        return a == null ? _error(404, 'NOT_FOUND', 'Recurso não encontrado') : _json(200, a);
      case ('GET', ['cases']):
        return _json(200, FakeData.page(FakeData.cases, options.queryParameters));
      case ('GET', ['cases', final id]):
        final c = FakeData.cases.where((e) => e['id'] == id).firstOrNull;
        return c == null ? _error(404, 'NOT_FOUND', 'Recurso não encontrado') : _json(200, c);
      case ('GET', ['notifications']):
        return _json(200, FakeData.page(FakeData.notifications, options.queryParameters));
      case ('GET', ['notifications', 'unread-count']):
        return _json(200, {'count': FakeData.notifications.where((n) => n['read'] == false).length});
      case ('GET', ['ping']):
        return _json(200, {'ok': true, 'user': user});
    }
    return _error(404, 'NOT_FOUND', 'Recurso não encontrado');
  }

  ResponseBody _login(Map<String, dynamic> body) {
    loginCalls++;
    final u = users[body['username']];
    if (u == null || body['password'] != password) {
      return _error(401, 'UNAUTHORIZED', 'Credenciais inválidas');
    }
    return _json(200, _issue(u['username'] as String));
  }

  ResponseBody _refreshToken(Map<String, dynamic> body) {
    refreshCalls++;
    final rt = body['refreshToken'];
    final username = _refresh.remove(rt); // rotação: o usado é sempre revogado
    if (failRefresh || username == null) {
      return _error(422, 'INVALID_REFRESH_TOKEN', 'Invalid refresh token');
    }
    return _json(200, _issue(username));
  }

  Map<String, dynamic> _issue(String username) {
    _seq++;
    final access = 'fake-access-$_seq-${_clock().microsecondsSinceEpoch}';
    final refresh = 'fake-refresh-$_seq-${_clock().microsecondsSinceEpoch}';
    _access[access] = _clock().add(accessTtl);
    _accessUser[access] = username;
    _refresh[refresh] = username;
    return {
      'accessToken': access,
      'refreshToken': refresh,
      'expiresIn': accessTtl.inSeconds,
      'user': users[username],
    };
  }

  String? _authenticate(RequestOptions o) {
    final h = o.headers['Authorization'] ?? o.headers['authorization'];
    if (h is! String || !h.startsWith('Bearer ')) return null;
    final token = h.substring(7);
    final exp = _access[token];
    if (exp == null || !exp.isAfter(_clock())) return null;
    return _accessUser[token];
  }

  static Map<String, dynamic> _body(Object? data) {
    if (data is Map) return data.cast<String, dynamic>();
    if (data is String && data.isNotEmpty) {
      final decoded = jsonDecode(data);
      if (decoded is Map) return decoded.cast<String, dynamic>();
    }
    return const {};
  }

  int _trace = 0;

  ResponseBody _error(int status, String code, String message) {
    _trace++;
    final traceId = 'fake-${_trace.toRadixString(16).padLeft(6, '0')}';
    return _json(status, {
      'timestamp': _clock().toUtc().toIso8601String(),
      'status': status,
      'error': code,
      'message': message,
      'code': code,
      'traceId': traceId,
    }, headers: {'x-trace-id': [traceId]});
  }

  static ResponseBody _json(int status, Object? data, {Map<String, List<String>> headers = const {}}) {
    if (data == null) return ResponseBody.fromString('', status, headers: headers);
    return ResponseBody.fromString(
      jsonEncode(data),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
        ...headers,
      },
    );
  }
}
