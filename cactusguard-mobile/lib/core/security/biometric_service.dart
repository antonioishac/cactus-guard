import 'package:flutter/foundation.dart';
import 'package:local_auth/local_auth.dart';

import '../utils/logger.dart';

/// Abstração sobre local_auth (biometria com fallback para PIN/padrão do aparelho).
abstract interface class BiometricService {
  Future<bool> isAvailable();
  Future<bool> authenticate(String reason);
}

class LocalAuthBiometricService implements BiometricService {
  LocalAuthBiometricService([LocalAuthentication? auth]) : _auth = auth ?? LocalAuthentication();
  final LocalAuthentication _auth;

  @override
  Future<bool> isAvailable() async {
    if (kIsWeb) return false;
    try {
      return await _auth.isDeviceSupported() && await _auth.canCheckBiometrics;
    } catch (e) {
      AppLogger.warn('Biometria indisponível (${e.runtimeType})');
      return false;
    }
  }

  @override
  Future<bool> authenticate(String reason) async {
    if (kIsWeb) return false;
    try {
      // biometricOnly=false: permite o PIN/padrão do aparelho como fallback do sistema.
      return await _auth.authenticate(localizedReason: reason, persistAcrossBackgrounding: true);
    } catch (e) {
      AppLogger.warn('Autenticação biométrica falhou (${e.runtimeType})');
      return false;
    }
  }
}

/// Implementação controlável para testes.
class FakeBiometricService implements BiometricService {
  FakeBiometricService({this.available = true, this.result = true});
  bool available;
  bool result;
  int calls = 0;

  @override
  Future<bool> isAvailable() async => available;

  @override
  Future<bool> authenticate(String reason) async {
    calls++;
    return result;
  }
}
