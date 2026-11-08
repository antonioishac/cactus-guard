import 'package:flutter/foundation.dart';

import '../utils/logger.dart';
import 'biometric_service.dart';
import 'secure_store.dart';

/// Bloqueio por biometria ao voltar do background após [lockAfter] (configurável via
/// `BIOMETRIC_LOCK_SECONDS`). Só vale com biometria ativada pelo usuário no Perfil.
/// Fallback: "Entrar com usuário e senha" encerra a sessão e leva ao login.
class AppLockController extends ChangeNotifier {
  AppLockController({
    required this.lockAfter,
    required this._biometrics,
    required this._storage,
    DateTime Function()? clock,
  }) : _clock = clock ?? DateTime.now;

  final Duration lockAfter;
  final BiometricService _biometrics;
  final SecureStore _storage;
  final DateTime Function() _clock;

  bool _enabled = false;
  bool _locked = false;
  DateTime? _backgroundAt;

  bool get biometricEnabled => _enabled;
  bool get locked => _locked;

  Future<void> load() async {
    try {
      _enabled = await _storage.read(SecureKeys.biometricEnabled) == 'true';
    } catch (_) {
      _enabled = false;
    }
    notifyListeners();
  }

  Future<bool> isAvailable() => _biometrics.isAvailable();

  /// Ativar exige uma autenticação bem-sucedida (prova de posse).
  Future<bool> setEnabled(bool value) async {
    if (value) {
      if (!await _biometrics.isAvailable()) return false;
      if (!await _biometrics.authenticate('Confirme para ativar o desbloqueio por biometria')) return false;
    }
    _enabled = value;
    await _storage.write(SecureKeys.biometricEnabled, value.toString());
    notifyListeners();
    return true;
  }

  void onBackground() => _backgroundAt ??= _clock();

  /// Ao voltar ao primeiro plano: bloqueia se a biometria está ativa e passou de [lockAfter].
  void onForeground({required bool authenticated}) {
    final since = _backgroundAt;
    _backgroundAt = null;
    if (!authenticated || !_enabled || since == null || _locked) return;
    if (_clock().difference(since) >= lockAfter) {
      AppLogger.info('App bloqueado após período em background');
      _locked = true;
      notifyListeners();
    }
  }

  Future<bool> unlock() async {
    final ok = await _biometrics.authenticate('Desbloqueie o CactusGuard');
    if (ok) {
      _locked = false;
      notifyListeners();
    }
    return ok;
  }

  /// No logout/expiração: remove o bloqueio (o login substitui a biometria).
  void reset() {
    _backgroundAt = null;
    if (_locked) {
      _locked = false;
      notifyListeners();
    }
  }
}
