import 'package:cactusguard_mobile/core/api/api_error.dart';
import 'package:cactusguard_mobile/core/auth/auth_models.dart';
import 'package:cactusguard_mobile/core/providers.dart';
import 'package:cactusguard_mobile/core/security/secure_store.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../helpers.dart';

void main() {
  late TestEnv env;
  late ProviderContainer c;

  setUp(() async {
    env = TestEnv();
    c = env.container();
    await c.read(authControllerProvider).login('analista', 'dev12345');
  });

  tearDown(() => c.dispose());

  test('login guarda refresh token só no armazenamento seguro e access token em memória', () {
    final store = c.read(sessionStoreProvider);
    expect(store.status, AuthStatus.authenticated);
    expect(store.user!.roles, [Role.analyst]);
    expect(env.storage.values[SecureKeys.refreshToken], store.refreshToken);
    expect(env.storage.values.values, isNot(contains(store.accessToken)));
  });

  test('duas requisições 401 simultâneas disparam UM único refresh e ambas são repetidas', () async {
    final store = c.read(sessionStoreProvider);
    final dio = c.read(dioProvider);
    final oldRefresh = store.refreshToken!;
    env.backend
      ..refreshLatency = const Duration(milliseconds: 40)
      ..expireAccessTokens();

    final results = await Future.wait([dio.get<dynamic>('/ping'), dio.get<dynamic>('/ping')]);

    expect(results.map((r) => r.statusCode), [200, 200]);
    expect(env.backend.refreshCalls, 1);
    expect(store.refreshCount, 1);
    // rotação: o refresh token antigo foi revogado e o novo persistido
    expect(store.refreshToken, isNot(oldRefresh));
    expect(env.backend.isRefreshValid(oldRefresh), isFalse);
    expect(env.storage.values[SecureKeys.refreshToken], store.refreshToken);
  });

  test('requisição feita durante refresh em curso espera e sai com o token novo', () async {
    final store = c.read(sessionStoreProvider);
    final dio = c.read(dioProvider);
    env.backend
      ..refreshLatency = const Duration(milliseconds: 40)
      ..expireAccessTokens();

    final first = dio.get<dynamic>('/ping');
    await Future<void>.delayed(const Duration(milliseconds: 10));
    expect(store.refreshing, isTrue);
    final second = dio.get<dynamic>('/ping');

    await Future.wait([first, second]);
    expect(env.backend.refreshCalls, 1);
  });

  test('falha no refresh encerra a sessão (logout) com aviso de sessão expirada', () async {
    final store = c.read(sessionStoreProvider);
    final dio = c.read(dioProvider);
    env.backend
      ..failRefresh = true
      ..expireAccessTokens();

    await expectLater(
      dio.get<dynamic>('/ping'),
      throwsA(isA<DioException>().having((e) => e.response?.statusCode, 'status', 401)),
    );

    expect(store.status, AuthStatus.anonymous);
    expect(store.accessToken, isNull);
    expect(store.notice, ClearReason.expired);
    expect(env.storage.values.containsKey(SecureKeys.refreshToken), isFalse);
    expect(env.backend.refreshCalls, 1);
  });

  test('logout revoga refresh no backend, remove o aparelho e limpa armazenamento', () async {
    final store = c.read(sessionStoreProvider);
    final rt = store.refreshToken!;
    // flutter_test usa TargetPlatform.android: o login registrou o aparelho (POST /devices)
    expect(env.backend.registeredDevices.single,
        {'token': 'test-device-token', 'platform': 'ANDROID', 'appVersion': 'test', 'user': 'analista'});

    await c.read(authControllerProvider).logout();

    expect(env.backend.registeredDevices, isEmpty, reason: 'DELETE /devices/{token} no logout');
    expect(env.backend.requests, contains('DELETE /devices/test-device-token'));
    expect(env.backend.logoutCalls, 1);
    expect(env.backend.isRefreshValid(rt), isFalse);
    expect(store.status, AuthStatus.anonymous);
    expect(store.notice, isNull);
    expect(env.storage.values, isEmpty);
  });

  test('restore reabre a sessão a partir do refresh token guardado', () async {
    final rt = c.read(sessionStoreProvider).refreshToken!;
    final c2 = ProviderContainer(overrides: env.overrides);
    addTearDown(c2.dispose);
    expect(env.storage.values[SecureKeys.refreshToken], rt);

    await c2.read(authControllerProvider).restore();

    final s2 = c2.read(sessionStoreProvider);
    expect(s2.status, AuthStatus.authenticated);
    expect(s2.user!.username, 'analista');
  });

  test('credenciais inválidas viram ApiError amigável com traceId', () async {
    final ctrl = c.read(authControllerProvider);
    await expectLater(
      ctrl.login('analista', 'errada'),
      throwsA(isA<ApiError>()
          .having((e) => e.status, 'status', 401)
          .having((e) => e.message, 'message', 'Usuário ou senha inválidos.')
          .having((e) => e.traceId, 'traceId', isNotNull)),
    );
  });
}
