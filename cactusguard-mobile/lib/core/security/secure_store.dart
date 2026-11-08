import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Chave-valor seguro. Produção: Android Keystore / iOS Keychain via flutter_secure_storage.
/// **Nunca** usar SharedPreferences para tokens (spec 024).
abstract interface class SecureStore {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);
}

class FlutterSecureStore implements SecureStore {
  FlutterSecureStore([FlutterSecureStorage? storage])
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(),
              // Sem sincronização via iCloud e só após o primeiro desbloqueio deste aparelho.
              iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock_this_device),
            );

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String value) => _storage.write(key: key, value: value);

  @override
  Future<void> delete(String key) => _storage.delete(key: key);
}

/// Implementação em memória para testes e modo mock no web.
class MemorySecureStore implements SecureStore {
  final Map<String, String> values = {};

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String value) async => values[key] = value;

  @override
  Future<void> delete(String key) async => values.remove(key);
}

/// Chaves usadas no armazenamento seguro.
abstract final class SecureKeys {
  static const refreshToken = 'cg.rt';
  static const biometricEnabled = 'cg.biometric';
}
