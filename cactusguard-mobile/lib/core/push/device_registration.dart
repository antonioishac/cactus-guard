import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../utils/logger.dart';

/// Contrato do backend (trilha de push):
/// - `POST /api/v1/devices` `{token, platform: ANDROID|IOS, appVersion}` → 201
/// - `DELETE /api/v1/devices/{token}` no logout → 204
///
/// Falhas nunca bloqueiam login/logout (apenas registradas, sem o token).
class DeviceRegistrationApi {
  DeviceRegistrationApi(this._dio, {required this.appVersion, this._platform});

  final Dio _dio;
  final String appVersion;
  final TargetPlatform? _platform;

  String? get platformCode {
    if (kIsWeb && _platform == null) return null;
    return switch (_platform ?? defaultTargetPlatform) {
      TargetPlatform.android => 'ANDROID',
      TargetPlatform.iOS => 'IOS',
      _ => null,
    };
  }

  Future<bool> register(String token) async {
    final platform = platformCode;
    if (platform == null) return false;
    try {
      await _dio.post<void>('/devices', data: {'token': token, 'platform': platform, 'appVersion': appVersion});
      return true;
    } catch (e) {
      AppLogger.warn('Registro do aparelho para push falhou (${e.runtimeType})');
      return false;
    }
  }

  Future<bool> unregister(String token) async {
    if (platformCode == null) return false;
    try {
      await _dio.delete<void>('/devices/${Uri.encodeComponent(token)}');
      return true;
    } catch (e) {
      AppLogger.warn('Remoção do aparelho falhou (${e.runtimeType})');
      return false;
    }
  }
}
