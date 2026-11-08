import 'dart:async';

import 'package:flutter/foundation.dart';

import '../security/secure_store.dart';
import '../utils/logger.dart';
import 'auth_api.dart';
import 'auth_models.dart';

/// Fonte única da sessão (equivalente ao `authStore` + `refreshSession` do web).
///
/// Armazenamento (spec 024):
/// - access token: **somente memória** (recuperado via refresh ao abrir o app);
/// - refresh token: memória + [SecureStore] (Keystore/Keychain). Rotacionado a cada refresh.
class SessionStore extends ChangeNotifier {
  SessionStore({required this._storage, required this._authApi});

  final SecureStore _storage;
  final AuthApi _authApi;

  AuthStatus _status = AuthStatus.unknown;
  SessionUser? _user;
  String? _accessToken;
  String? _refreshToken;
  ClearReason? _notice;
  Future<String>? _pendingRefresh;
  int _refreshCount = 0;

  AuthStatus get status => _status;
  SessionUser? get user => _user;
  String? get accessToken => _accessToken;
  String? get refreshToken => _refreshToken;
  bool get isAuthenticated => _status == AuthStatus.authenticated;
  List<Role> get roles => _user?.roles ?? const [];

  /// Mensagem para a tela de login (sessão expirada, inatividade...). Consumida uma vez.
  ClearReason? get notice => _notice;
  void consumeNotice() => _notice = null;

  /// true enquanto há refresh em andamento (requisições novas aguardam — spec 022).
  bool get refreshing => _pendingRefresh != null;
  Future<String>? get pendingRefresh => _pendingRefresh;

  /// Quantas chamadas reais de refresh foram feitas (diagnóstico/testes).
  int get refreshCount => _refreshCount;

  /// Splash: tenta reabrir a sessão com o refresh token guardado.
  Future<void> restore() async {
    String? stored;
    try {
      stored = await _storage.read(SecureKeys.refreshToken);
    } catch (e) {
      AppLogger.error('Falha ao ler armazenamento seguro', e);
    }
    if (stored == null || stored.isEmpty) {
      _status = AuthStatus.anonymous;
      notifyListeners();
      return;
    }
    _refreshToken = stored;
    try {
      await refresh();
    } catch (_) {
      // refresh() já limpou a sessão.
    }
  }

  Future<void> setSession(AuthResponse resp) async {
    _accessToken = resp.accessToken;
    _refreshToken = resp.refreshToken;
    _user = resp.user;
    _status = AuthStatus.authenticated;
    _notice = null;
    await _storage.write(SecureKeys.refreshToken, resp.refreshToken);
    notifyListeners();
  }

  /// Fila única: chamadas simultâneas recebem a MESMA future; só uma requisição de refresh sai.
  /// Em falha, a sessão é encerrada com [ClearReason.expired].
  Future<String> refresh() {
    final pending = _pendingRefresh;
    if (pending != null) return pending;
    final rt = _refreshToken;
    if (rt == null) {
      unawaited(clear(ClearReason.expired));
      return Future.error(StateError('sem refresh token'));
    }
    final completer = Completer<String>();
    _pendingRefresh = completer.future;
    _refreshCount++;
    notifyListeners();
    () async {
      try {
        final resp = await _authApi.refresh(rt);
        _pendingRefresh = null;
        await setSession(resp);
        completer.complete(resp.accessToken);
      } catch (e) {
        AppLogger.warn('Refresh de sessão falhou; encerrando sessão');
        _pendingRefresh = null;
        await clear(ClearReason.expired);
        completer.completeError(e);
      }
    }();
    return completer.future;
  }

  /// Limpa memória e armazenamento seguro. Não chama o backend (ver AuthController.logout).
  Future<void> clear([ClearReason reason = ClearReason.logout]) async {
    _accessToken = null;
    _refreshToken = null;
    _user = null;
    _status = AuthStatus.anonymous;
    _notice = reason == ClearReason.logout ? null : reason;
    try {
      await _storage.delete(SecureKeys.refreshToken);
    } catch (e) {
      AppLogger.error('Falha ao limpar armazenamento seguro', e);
    }
    notifyListeners();
  }
}
