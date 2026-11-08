import 'push_payload.dart';

/// Converte deep links externos em rotas internas do go_router.
///
/// Aceitos: `cactusguard://alerts/{uuid}` e `cactusguard://cases/{uuid}`. Qualquer outra coisa → `null`
/// (o router manda para a home). Também aceita a forma já normalizada `/alerts/{uuid}`.
abstract final class DeepLink {
  static const scheme = 'cactusguard';
  static const _hosts = {'alerts', 'cases'};

  static bool isExternal(Uri uri) => uri.scheme == scheme;

  static String? toLocation(Uri uri) {
    final List<String> parts;
    if (uri.scheme == scheme) {
      // cactusguard://alerts/{id}: host = alerts, path = /{id}
      parts = [if (uri.host.isNotEmpty) uri.host, ...uri.pathSegments.where((s) => s.isNotEmpty)];
    } else if (uri.scheme.isEmpty) {
      parts = uri.pathSegments.where((s) => s.isNotEmpty).toList();
    } else {
      return null;
    }
    if (parts.length != 2 || !_hosts.contains(parts[0]) || !PushPayload.isValidId(parts[1])) return null;
    return '/${parts[0]}/${parts[1].toLowerCase()}';
  }
}
