import 'package:cactusguard_mobile/core/auth/auth_models.dart';
import 'package:cactusguard_mobile/core/auth/permissions.dart';
import 'package:cactusguard_mobile/core/router/route_guard.dart';
import 'package:cactusguard_mobile/features/home/presentation/home_shell.dart';
import 'package:flutter_test/flutter_test.dart';

String? r(String loc, {AuthStatus status = AuthStatus.authenticated, List<Role> roles = const [Role.analyst], bool locked = false}) =>
    resolveRedirect(uri: Uri.parse(loc), status: status, roles: roles, locked: locked);

void main() {
  group('guarda de rota por papel', () {
    test('Aprovações: só SUPERVISOR', () {
      expect(r('/approvals', roles: [Role.analyst]), Routes.unauthorized);
      expect(r('/approvals', roles: [Role.admin]), Routes.unauthorized);
      expect(r('/approvals', roles: [Role.auditor]), Routes.unauthorized);
      expect(r('/approvals', roles: [Role.supervisor]), isNull);
      expect(r('/approvals', roles: [Role.analyst, Role.supervisor]), isNull);
    });

    test('rotas comuns liberadas para todos os papéis autenticados', () {
      for (final role in Role.values) {
        expect(r('/alerts', roles: [role]), isNull);
        expect(r('/cases', roles: [role]), isNull);
        expect(r('/profile', roles: [role]), isNull);
      }
    });

    test('bottom navigation: Aprovações só aparece para SUPERVISOR', () {
      expect(visibleNavItems([Role.analyst]).map((i) => i.label), isNot(contains('Aprovações')));
      expect(visibleNavItems([Role.supervisor]).map((i) => i.label), contains('Aprovações'));
      expect(visibleNavItems([Role.analyst]), hasLength(4));
    });
  });

  group('sessão', () {
    test('anônimo vai para login preservando o destino', () {
      expect(r('/alerts/123', status: AuthStatus.anonymous), '/login?from=%2Falerts%2F123');
      expect(r('/login', status: AuthStatus.anonymous), isNull);
    });

    test('sessão desconhecida passa pela splash', () {
      expect(r('/cases', status: AuthStatus.unknown), '/splash?from=%2Fcases');
      expect(r('/splash', status: AuthStatus.unknown), isNull);
    });

    test('autenticado em rota transitória volta ao destino ou à home', () {
      expect(r('/login?from=%2Fcases'), '/cases');
      expect(r('/splash'), Routes.home);
      expect(r('/login?from=%2F%2Fevil.com'), Routes.home, reason: 'open redirect bloqueado');
    });

    test('bloqueado vai para a tela de biometria', () {
      expect(r('/alerts', locked: true), '/lock?from=%2Falerts');
      expect(r('/lock', locked: true), isNull);
      expect(r('/lock?from=%2Falerts'), '/alerts');
    });
  });

  test('matriz de permissões igual ao web', () {
    expect(can([Role.analyst], AppAction.caseDecide), isTrue);
    expect(can([Role.analyst], AppAction.caseApprove), isFalse);
    expect(can([Role.supervisor], AppAction.caseApprove), isTrue);
    expect(can([Role.auditor], AppAction.alertAssume), isFalse);
    expect(can([Role.admin], AppAction.usersManage), isTrue);
    expect(can([Role.admin], AppAction.piiReveal), isFalse);
    expect(permissionMatrix.keys, containsAll(AppAction.values));
  });

  test('normalização de papéis (ROLE_ e desconhecidos)', () {
    expect(Role.normalize(['ROLE_SUPERVISOR', 'analyst', 'XPTO']), [Role.supervisor, Role.analyst]);
  });
}
