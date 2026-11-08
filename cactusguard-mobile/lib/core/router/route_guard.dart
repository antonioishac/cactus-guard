import '../auth/auth_models.dart';
import '../auth/permissions.dart';
import '../push/deep_link.dart';

/// Rotas da aplicação.
abstract final class Routes {
  static const splash = '/splash';
  static const login = '/login';
  static const lock = '/lock';
  static const unauthorized = '/unauthorized';
  static const home = alerts;
  static const alerts = '/alerts';
  static const cases = '/cases';
  static const approvals = '/approvals';
  static const notifications = '/notifications';
  static const profile = '/profile';

  static String alertDetail(String id) => '$alerts/$id';
  static String caseDetail(String id) => '$cases/$id';
}

/// Papéis exigidos por prefixo de rota (conveniência de UI; o backend é a barreira real).
const Map<String, List<Role>> routeRoles = {
  Routes.approvals: [Role.supervisor],
};

bool canAccess(String location, List<Role> roles) {
  for (final entry in routeRoles.entries) {
    if (location == entry.key || location.startsWith('${entry.key}/')) {
      if (!hasAnyRole(roles, entry.value)) return false;
    }
  }
  return true;
}

const _transient = {Routes.splash, Routes.login, Routes.lock, '/', ''};

/// Só aceita destino interno (`/x`), nunca `//host` nem rotas transitórias.
String? sanitizeFrom(String? from) {
  if (from == null || from.isEmpty || !from.startsWith('/') || from.startsWith('//')) return null;
  final path = Uri.tryParse(from)?.path ?? '';
  if (_transient.contains(path) || path == Routes.unauthorized) return null;
  return from;
}

String _withFrom(String target, String? from) {
  final f = sanitizeFrom(from);
  return f == null ? target : Uri(path: target, queryParameters: {'from': f}).toString();
}

/// Decide o redirecionamento global (função pura, testada isoladamente).
///
/// Ordem: deep link externo → sessão desconhecida (splash) → anônimo (login) → bloqueado (biometria)
/// → rotas transitórias → papel exigido.
String? resolveRedirect({
  required Uri uri,
  required AuthStatus status,
  required List<Role> roles,
  required bool locked,
}) {
  if (DeepLink.isExternal(uri)) return DeepLink.toLocation(uri) ?? Routes.home;

  final loc = uri.path.isEmpty ? '/' : uri.path;
  final here = uri.toString();
  final carried = uri.queryParameters['from'];

  switch (status) {
    case AuthStatus.unknown:
      if (loc == Routes.splash) return null;
      return _withFrom(Routes.splash, _transient.contains(loc) ? carried : here);
    case AuthStatus.anonymous:
      if (loc == Routes.login) return null;
      return _withFrom(Routes.login, _transient.contains(loc) ? carried : here);
    case AuthStatus.authenticated:
      if (locked) {
        if (loc == Routes.lock) return null;
        return _withFrom(Routes.lock, _transient.contains(loc) ? carried : here);
      }
      if (_transient.contains(loc)) return sanitizeFrom(carried) ?? Routes.home;
      if (!canAccess(loc, roles)) return Routes.unauthorized;
      return null;
  }
}
