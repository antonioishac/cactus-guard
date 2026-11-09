import 'package:flutter/foundation.dart';

/// Modo do serviço de push. `noop` é o padrão até existir projeto Firebase (spec 024, decisões).
enum PushMode { noop, local }

/// Configuração de ambiente lida de `--dart-define`.
///
/// - `API_URL`: base do backend, sem `/api/v1` (padrão `http://10.0.2.2:8080`, o host visto pelo emulador Android).
/// - `USE_MOCK`: `true` usa o backend fake em memória (sem servidor).
/// - `IDLE_TIMEOUT_MIN`: logout por inatividade (padrão 15, igual ao web).
/// - `BIOMETRIC_LOCK_SECONDS`: tempo em background que exige biometria ao voltar (padrão 30).
/// - `PUSH_MODE`: `noop` (padrão) ou `local` (notificação local simulada para dev).
/// - `APP_VERSION`: enviada no registro do aparelho.
@immutable
class AppConfig {
  const AppConfig({
    required this.apiUrl,
    required this.useMock,
    required this.idleTimeout,
    required this.biometricLockAfter,
    required this.pushMode,
    required this.appVersion,
  });

  static const _apiUrl = String.fromEnvironment('API_URL', defaultValue: 'http://10.0.2.2:8080');
  static const _useMock = bool.fromEnvironment('USE_MOCK');
  static const _idleMin = int.fromEnvironment('IDLE_TIMEOUT_MIN', defaultValue: 15);
  static const _lockSec = int.fromEnvironment('BIOMETRIC_LOCK_SECONDS', defaultValue: 30);
  static const _push = String.fromEnvironment('PUSH_MODE', defaultValue: 'noop');
  static const _version = String.fromEnvironment('APP_VERSION', defaultValue: '0.5.0');

  factory AppConfig.fromEnvironment() => AppConfig(
        apiUrl: _apiUrl,
        useMock: _useMock,
        idleTimeout: Duration(minutes: _idleMin),
        biometricLockAfter: Duration(seconds: _lockSec),
        pushMode: _push == 'local' ? PushMode.local : PushMode.noop,
        appVersion: _version,
      );

  /// Configuração usada em testes: backend fake, push desligado.
  factory AppConfig.test({
    Duration idleTimeout = const Duration(minutes: 15),
    Duration biometricLockAfter = const Duration(seconds: 30),
  }) =>
      AppConfig(
        apiUrl: 'http://localhost:8080',
        useMock: true,
        idleTimeout: idleTimeout,
        biometricLockAfter: biometricLockAfter,
        pushMode: PushMode.noop,
        appVersion: 'test',
      );

  final String apiUrl;
  final bool useMock;
  final Duration idleTimeout;
  final Duration biometricLockAfter;
  final PushMode pushMode;
  final String appVersion;

  /// Base usada pelo Dio (todos os endpoints ficam sob `/api/v1`).
  String get apiBaseUrl => '${apiUrl.replaceAll(RegExp(r'/+$'), '')}/api/v1';

  /// Em release, HTTP sem TLS é proibido (o `network_security_config` do Android não se aplica ao
  /// HttpClient do Dart; esta verificação é a barreira efetiva).
  void validate({bool isRelease = kReleaseMode}) {
    if (isRelease && !useMock && !apiUrl.startsWith('https://')) {
      throw StateError('API_URL deve usar HTTPS em release.');
    }
  }
}
