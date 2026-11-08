import '../api/api_error.dart';
import '../push/device_registration.dart';
import '../push/push_service.dart';
import '../security/app_lock.dart';
import '../utils/logger.dart';
import 'auth_api.dart';
import 'auth_models.dart';
import 'session_store.dart';

/// Casos de uso de sessão: login, restauração, logout. Coordena [SessionStore], bloqueio biométrico e
/// registro do aparelho para push.
class AuthController {
  AuthController({
    required this.store,
    required this.authApi,
    required this.lock,
    required this.push,
    required this.devices,
  });

  final SessionStore store;
  final AuthApi authApi;
  final AppLockController lock;
  final PushService push;
  final DeviceRegistrationApi devices;

  /// Lança [ApiError] com mensagem amigável.
  Future<void> login(String username, String password) async {
    try {
      final resp = await authApi.login(LoginRequest(username: username.trim(), password: password));
      await store.setSession(resp);
      lock.reset();
      await _registerDevice();
    } catch (e) {
      throw ApiError.from(e);
    }
  }

  Future<void> restore() async {
    await lock.load();
    await store.restore();
    if (store.isAuthenticated) await _registerDevice();
  }

  /// Logout: remove o aparelho do push, revoga o refresh token no backend e limpa tudo local.
  /// Falhas de rede não impedem a limpeza local.
  Future<void> logout([ClearReason reason = ClearReason.logout]) async {
    if (store.isAuthenticated) {
      final token = await _safeToken();
      if (token != null) await devices.unregister(token);
    }
    final rt = store.refreshToken;
    if (rt != null) {
      try {
        await authApi.logout(rt);
      } catch (e) {
        AppLogger.warn('Logout no backend falhou (${e.runtimeType}); limpando sessão local');
      }
    }
    await store.clear(reason);
    lock.reset();
  }

  Future<void> _registerDevice() async {
    final token = await _safeToken();
    if (token != null) await devices.register(token);
  }

  Future<String?> _safeToken() async {
    try {
      return await push.getToken();
    } catch (_) {
      return null;
    }
  }
}
