import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'api/auth_interceptor.dart';
import 'api/mock/fake_backend.dart';
import 'api/safe_log_interceptor.dart';
import 'auth/auth_api.dart';
import 'auth/auth_controller.dart';
import 'auth/auth_models.dart';
import 'auth/session_store.dart';
import 'config/app_config.dart';
import 'push/device_registration.dart';
import 'push/push_service.dart';
import 'security/app_lock.dart';
import 'security/biometric_service.dart';
import 'security/secure_store.dart';

/// Raiz de composição (Riverpod, sem codegen). Testes sobrescrevem [appConfigProvider],
/// [secureStoreProvider], [fakeBackendProvider], [biometricServiceProvider] e [pushServiceProvider].

final appConfigProvider = Provider<AppConfig>((ref) => AppConfig.fromEnvironment());

final secureStoreProvider = Provider<SecureStore>((ref) => FlutterSecureStore());

final fakeBackendProvider = Provider<FakeBackend>((ref) => FakeBackend());

/// Adapter HTTP: backend fake em modo mock; `null` = adapter padrão do Dio (rede real).
final httpAdapterProvider = Provider<HttpClientAdapter?>((ref) {
  return ref.watch(appConfigProvider).useMock ? ref.watch(fakeBackendProvider) : null;
});

Dio _newDio(Ref ref) {
  final config = ref.watch(appConfigProvider);
  final dio = Dio(BaseOptions(
    baseUrl: config.apiBaseUrl,
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 30),
    contentType: Headers.jsonContentType,
    responseType: ResponseType.json,
  ));
  final adapter = ref.watch(httpAdapterProvider);
  if (adapter != null) dio.httpClientAdapter = adapter;
  if (kDebugMode) dio.interceptors.add(SafeLogInterceptor());
  return dio;
}

/// Dio sem interceptor JWT: usado só por login/refresh/logout (evita laço de 401).
final bareDioProvider = Provider<Dio>((ref) => _newDio(ref));

final authApiProvider = Provider<AuthApi>((ref) => AuthApi(ref.watch(bareDioProvider)));

final sessionStoreProvider = Provider<SessionStore>((ref) {
  final store = SessionStore(storage: ref.watch(secureStoreProvider), authApi: ref.watch(authApiProvider));
  ref.onDispose(store.dispose);
  return store;
});

/// Dio autenticado (JWT + refresh em fila). Use este em todos os repositórios de feature.
final dioProvider = Provider<Dio>((ref) {
  final dio = _newDio(ref);
  dio.interceptors.add(AuthInterceptor(session: ref.watch(sessionStoreProvider), dio: dio));
  return dio;
});

final biometricServiceProvider = Provider<BiometricService>((ref) => LocalAuthBiometricService());

final appLockProvider = Provider<AppLockController>((ref) {
  final lock = AppLockController(
    lockAfter: ref.watch(appConfigProvider).biometricLockAfter,
    biometrics: ref.watch(biometricServiceProvider),
    storage: ref.watch(secureStoreProvider),
  );
  ref.onDispose(lock.dispose);
  return lock;
});

final pushServiceProvider = Provider<PushService>((ref) {
  final service = switch (ref.watch(appConfigProvider).pushMode) {
    PushMode.local => LocalSimulatedPushService(),
    PushMode.noop => NoopPushService(),
  };
  ref.onDispose(service.dispose);
  return service;
});

final deviceRegistrationProvider = Provider<DeviceRegistrationApi>(
  (ref) => DeviceRegistrationApi(ref.watch(dioProvider), appVersion: ref.watch(appConfigProvider).appVersion),
);

final authControllerProvider = Provider<AuthController>((ref) => AuthController(
      store: ref.watch(sessionStoreProvider),
      authApi: ref.watch(authApiProvider),
      lock: ref.watch(appLockProvider),
      push: ref.watch(pushServiceProvider),
      devices: ref.watch(deviceRegistrationProvider),
    ));

/// Visão imutável da sessão para widgets (`ref.watch(sessionProvider)`).
@immutable
class SessionView {
  const SessionView({required this.status, this.user});
  final AuthStatus status;
  final SessionUser? user;
  List<Role> get roles => user?.roles ?? const [];
}

class SessionNotifier extends Notifier<SessionView> {
  @override
  SessionView build() {
    final store = ref.watch(sessionStoreProvider);
    void listener() => state = SessionView(status: store.status, user: store.user);
    store.addListener(listener);
    ref.onDispose(() => store.removeListener(listener));
    return SessionView(status: store.status, user: store.user);
  }
}

final sessionProvider = NotifierProvider<SessionNotifier, SessionView>(SessionNotifier.new);

/// Reage ao estado do bloqueio biométrico (ex.: switch no Perfil).
class BiometricEnabledNotifier extends Notifier<bool> {
  @override
  bool build() {
    final lock = ref.watch(appLockProvider);
    void listener() => state = lock.biometricEnabled;
    lock.addListener(listener);
    ref.onDispose(() => lock.removeListener(listener));
    return lock.biometricEnabled;
  }
}

final biometricEnabledProvider = NotifierProvider<BiometricEnabledNotifier, bool>(BiometricEnabledNotifier.new);
