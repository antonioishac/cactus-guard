import 'package:dio/dio.dart';

import 'auth_models.dart';

/// Endpoints de autenticação (spec 033). Usa um Dio **sem** o interceptor JWT para evitar laço de 401.
class AuthApi {
  AuthApi(this._dio);
  final Dio _dio;

  Future<AuthResponse> login(LoginRequest request) async {
    final res = await _dio.post<Map<String, dynamic>>('/auth/login', data: request.toJson());
    return AuthResponse.fromJson(res.data!);
  }

  /// O backend **rotaciona** o refresh token: o enviado é revogado e um novo vem na resposta.
  Future<AuthResponse> refresh(String refreshToken) async {
    final res = await _dio.post<Map<String, dynamic>>('/auth/refresh', data: {'refreshToken': refreshToken});
    return AuthResponse.fromJson(res.data!);
  }

  /// Revoga o refresh token no backend (204).
  Future<void> logout(String refreshToken) async {
    await _dio.post<void>('/auth/logout', data: {'refreshToken': refreshToken});
  }
}
