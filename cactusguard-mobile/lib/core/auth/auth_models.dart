import 'package:flutter/foundation.dart';

/// Papéis do backend (nomes sem prefixo `ROLE_`).
enum Role {
  admin('ADMIN', 'Administrador'),
  supervisor('SUPERVISOR', 'Supervisor'),
  analyst('ANALYST', 'Analista'),
  auditor('AUDITOR', 'Auditor');

  const Role(this.code, this.label);
  final String code;
  final String label;

  static Role? tryParse(String raw) {
    final c = raw.replaceFirst(RegExp(r'^ROLE_'), '').toUpperCase();
    for (final r in Role.values) {
      if (r.code == c) return r;
    }
    return null;
  }

  /// Normaliza `ROLE_ANALYST` → [Role.analyst] e descarta papéis desconhecidos (igual ao web).
  static List<Role> normalize(Iterable<String> raw) =>
      raw.map(tryParse).whereType<Role>().toSet().toList(growable: false);
}

/// `POST /api/v1/auth/login`.
@immutable
class LoginRequest {
  const LoginRequest({required this.username, required this.password});
  final String username;
  final String password;

  Map<String, dynamic> toJson() => {'username': username, 'password': password};
}

/// Usuário autenticado (AuthResponse.UserInfo normalizado).
@immutable
class SessionUser {
  const SessionUser({
    required this.id,
    required this.username,
    required this.name,
    required this.roles,
    this.permissions = const [],
  });

  factory SessionUser.fromJson(Map<String, dynamic> json) => SessionUser(
        id: json['id'] as String,
        username: (json['username'] as String?) ?? '',
        name: (json['name'] as String?) ?? '',
        roles: Role.normalize(((json['roles'] as List?) ?? const []).cast<String>()),
        permissions: ((json['permissions'] as List?) ?? const []).cast<String>(),
      );

  final String id;
  final String username;
  final String name;
  final List<Role> roles;

  /// Spec 033 prevê; o backend ainda não envia.
  final List<String> permissions;

  bool hasRole(Role r) => roles.contains(r);
}

/// `AuthResponse` do backend (login e refresh). `expiresIn` em segundos.
@immutable
class AuthResponse {
  const AuthResponse({
    required this.accessToken,
    required this.refreshToken,
    required this.expiresIn,
    required this.user,
  });

  factory AuthResponse.fromJson(Map<String, dynamic> json) => AuthResponse(
        accessToken: json['accessToken'] as String,
        refreshToken: json['refreshToken'] as String,
        expiresIn: (json['expiresIn'] as num?)?.toInt() ?? 0,
        user: SessionUser.fromJson((json['user'] as Map).cast<String, dynamic>()),
      );

  final String accessToken;
  final String refreshToken;
  final int expiresIn;
  final SessionUser user;
}

/// Situação da sessão (espelha `AuthStatus` do web).
enum AuthStatus { unknown, authenticated, anonymous }

/// Motivo do encerramento — define a mensagem exibida no login.
enum ClearReason {
  logout(null),
  expired('Sua sessão expirou. Entre novamente.'),
  idle('Sessão encerrada por inatividade. Entre novamente.'),
  locked('Desbloqueio cancelado. Entre com usuário e senha.');

  const ClearReason(this.message);
  final String? message;
}
