import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../utils/logger.dart';

/// Preferências de push do usuário (`GET/PUT /api/v1/devices/preferences`).
@immutable
class PushPreferences {
  const PushPreferences({required this.criticalAlerts, required this.deadlines});

  factory PushPreferences.fromJson(Map<String, dynamic> json) => PushPreferences(
        criticalAlerts: json['criticalAlerts'] as bool? ?? true,
        deadlines: json['deadlines'] as bool? ?? true,
      );

  /// Push de novos alertas críticos.
  final bool criticalAlerts;

  /// Push de prazos (SLA).
  final bool deadlines;

  Map<String, dynamic> toJson() => {'criticalAlerts': criticalAlerts, 'deadlines': deadlines};

  PushPreferences copyWith({bool? criticalAlerts, bool? deadlines}) => PushPreferences(
        criticalAlerts: criticalAlerts ?? this.criticalAlerts,
        deadlines: deadlines ?? this.deadlines,
      );
}

/// Contrato do backend (trilha de push):
/// - `POST /api/v1/devices` `{token, platform: ANDROID|IOS, appVersion}` → 201
/// - `DELETE /api/v1/devices/{token}` no logout → 204
/// - `GET/PUT /api/v1/devices/preferences` `{criticalAlerts, deadlines}`
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

  /// `GET /api/v1/devices/preferences`. Lança [DioException] (use `ApiError.from`).
  Future<PushPreferences> getPreferences() async {
    final res = await _dio.get<Map<String, dynamic>>('/devices/preferences');
    return PushPreferences.fromJson(res.data ?? const {});
  }

  /// `PUT /api/v1/devices/preferences`.
  Future<PushPreferences> updatePreferences(PushPreferences prefs) async {
    final res = await _dio.put<Map<String, dynamic>>('/devices/preferences', data: prefs.toJson());
    return res.data == null ? prefs : PushPreferences.fromJson(res.data!);
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
