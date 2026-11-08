import 'dart:async';

import 'package:cactusguard_mobile/core/api/mock/fake_backend.dart';
import 'package:cactusguard_mobile/core/config/app_config.dart';
import 'package:cactusguard_mobile/core/providers.dart';
import 'package:cactusguard_mobile/core/push/push_payload.dart';
import 'package:cactusguard_mobile/core/push/push_service.dart';
import 'package:cactusguard_mobile/core/security/biometric_service.dart';
import 'package:cactusguard_mobile/core/security/secure_store.dart';
import 'package:cactusguard_mobile/core/utils/logger.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;

/// Push de teste: devolve um token fixo e permite simular toque em notificação.
class TestPushService implements PushService {
  TestPushService({this.token = 'test-device-token'});
  final String? token;
  final opened = StreamController<PushPayload>.broadcast();

  @override
  Future<void> init() async {}
  @override
  Future<String?> getToken() async => token;
  @override
  Stream<PushPayload> get onMessage => const Stream.empty();
  @override
  Stream<PushPayload> get onOpened => opened.stream;
  @override
  Future<PushPayload?> initialPayload() async => null;
  @override
  Future<void> dispose() async => opened.close();
}

class TestEnv {
  TestEnv({AppConfig? config, Duration latency = Duration.zero})
      : backend = FakeBackend(latency: latency),
        storage = MemorySecureStore(),
        biometrics = FakeBiometricService(),
        push = TestPushService(),
        config = config ?? AppConfig.test() {
    AppLogger.sink = (_) {}; // testes silenciosos
  }

  final FakeBackend backend;
  final MemorySecureStore storage;
  final FakeBiometricService biometrics;
  final TestPushService push;
  final AppConfig config;

  List<Override> get overrides => [
        appConfigProvider.overrideWithValue(config),
        fakeBackendProvider.overrideWithValue(backend),
        secureStoreProvider.overrideWithValue(storage),
        biometricServiceProvider.overrideWithValue(biometrics),
        pushServiceProvider.overrideWithValue(push),
      ];

  ProviderContainer container() => ProviderContainer(overrides: overrides);
}
