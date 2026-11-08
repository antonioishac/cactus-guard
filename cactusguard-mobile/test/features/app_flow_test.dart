import 'package:cactusguard_mobile/app.dart';
import 'package:cactusguard_mobile/core/config/app_config.dart';
import 'package:cactusguard_mobile/core/push/push_payload.dart';
import 'package:cactusguard_mobile/core/router/app_router.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../helpers.dart';

const alertId = 'a1e7c000-0000-4000-8000-000000000001';
const caseId = 'ca5e0000-0000-4000-8000-000000000003';

Future<TestEnv> _boot(WidgetTester tester) async {
  final env = TestEnv();
  await tester.pumpWidget(ProviderScope(overrides: env.overrides, child: const CactusGuardApp()));
  await tester.pumpAndSettle();
  return env;
}

Future<void> _login(WidgetTester tester, String user) async {
  await tester.enterText(find.byKey(const Key('login-username')), user);
  await tester.enterText(find.byKey(const Key('login-password')), 'dev12345');
  await tester.tap(find.byKey(const Key('login-submit')));
  await tester.pumpAndSettle();
}

GoRouter _router(WidgetTester tester) =>
    ProviderScope.containerOf(tester.element(find.byType(CactusGuardApp))).read(routerProvider);

String _path(WidgetTester tester) => _router(tester).routerDelegate.currentConfiguration.uri.path;

void main() {
  testWidgets('splash → login → home do analista (sem aba Aprovações)', (tester) async {
    await _boot(tester);
    expect(find.byKey(const Key('login-username')), findsOneWidget);

    await _login(tester, 'analista');

    expect(_path(tester), '/alerts');
    expect(find.text('Notificações'), findsWidgets);
    expect(find.text('Aprovações'), findsNothing);
  });

  testWidgets('login inválido mostra erro amigável', (tester) async {
    await _boot(tester);
    await tester.enterText(find.byKey(const Key('login-username')), 'analista');
    await tester.enterText(find.byKey(const Key('login-password')), 'errada');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('login-error')), findsOneWidget);
    expect(find.textContaining('Usuário ou senha inválidos.'), findsOneWidget);
  });

  testWidgets('validação do formulário de login', (tester) async {
    await _boot(tester);
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pump();
    expect(find.text('Informe o usuário'), findsOneWidget);
    expect(find.text('Informe a senha'), findsOneWidget);
  });

  testWidgets('supervisor vê Aprovações; analista é barrado na rota', (tester) async {
    await _boot(tester);
    await _login(tester, 'supervisor');
    expect(find.text('Aprovações'), findsWidgets);
    await tester.tap(find.text('Aprovações').last);
    await tester.pumpAndSettle();
    expect(_path(tester), '/approvals');

    // logout e entra como analista
    _router(tester).go('/profile');
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('profile-logout')));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Sair'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('login-username')), findsOneWidget);

    await _login(tester, 'analista');
    _router(tester).go('/approvals');
    await tester.pumpAndSettle();
    expect(_path(tester), '/unauthorized');
    expect(find.text('Acesso negado'), findsOneWidget);
  });

  testWidgets('deep link cactusguard://alerts/{id} abre o detalhe do alerta', (tester) async {
    await _boot(tester);
    await _login(tester, 'analista');

    _router(tester).go('cactusguard://alerts/$alertId');
    await tester.pumpAndSettle();
    expect(_path(tester), '/alerts/$alertId');
    expect(find.byKey(const Key('alert-detail-$alertId')), findsOneWidget);

    _router(tester).go('cactusguard://cases/$caseId');
    await tester.pumpAndSettle();
    expect(_path(tester), '/cases/$caseId');
    expect(find.byKey(const Key('case-detail-$caseId')), findsOneWidget);
  });

  testWidgets('deep link sem sessão passa pelo login e depois abre o destino', (tester) async {
    await _boot(tester);
    _router(tester).go('cactusguard://cases/$caseId');
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('login-username')), findsOneWidget);

    await _login(tester, 'analista');
    expect(_path(tester), '/cases/$caseId');
  });

  testWidgets('toque em notificação push navega para o detalhe', (tester) async {
    final env = await _boot(tester);
    await _login(tester, 'analista');

    env.push.opened.add(const PushPayload(type: PushType.alert, id: alertId));
    await tester.pumpAndSettle();
    expect(_path(tester), '/alerts/$alertId');
  });

  testWidgets('15 min sem interação encerram a sessão com aviso de inatividade', (tester) async {
    final env = await _boot(tester);
    await _login(tester, 'analista');
    final rt = env.storage.values.values.first;

    await tester.pump(const Duration(minutes: 14));
    await tester.tap(find.text('Cases').last); // interação reinicia a contagem
    await tester.pumpAndSettle();
    await tester.pump(const Duration(minutes: 14));
    expect(_path(tester), '/cases');

    await tester.pump(const Duration(minutes: 1, seconds: 1));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('login-username')), findsOneWidget);
    expect(find.text('Sessão encerrada por inatividade. Entre novamente.'), findsOneWidget);
    expect(env.backend.isRefreshValid(rt), isFalse, reason: 'refresh revogado no backend');
  });

  testWidgets('volta do background com biometria ativa → tela de bloqueio → desbloqueio', (tester) async {
    final env = TestEnv(config: AppConfig.test(biometricLockAfter: Duration.zero));
    await tester.pumpWidget(ProviderScope(overrides: env.overrides, child: const CactusGuardApp()));
    await tester.pumpAndSettle();
    await _login(tester, 'analista');

    _router(tester).go('/profile');
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('profile-biometric')));
    await tester.pumpAndSettle();
    expect(tester.widget<SwitchListTile>(find.byKey(const Key('profile-biometric'))).value, isTrue);

    env.biometrics.result = false; // primeira tentativa falha
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();
    expect(_path(tester), '/lock');
    expect(find.text('Não foi possível confirmar sua identidade.'), findsOneWidget);

    env.biometrics.result = true;
    await tester.tap(find.byKey(const Key('lock-unlock')));
    await tester.pumpAndSettle();
    expect(_path(tester), '/profile');
  });

  testWidgets('bloqueio: "Entrar com usuário e senha" encerra a sessão', (tester) async {
    final env = TestEnv(config: AppConfig.test(biometricLockAfter: Duration.zero));
    await tester.pumpWidget(ProviderScope(overrides: env.overrides, child: const CactusGuardApp()));
    await tester.pumpAndSettle();
    await _login(tester, 'analista');
    _router(tester).go('/profile');
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('profile-biometric')));
    await tester.pumpAndSettle();

    env.biometrics.result = false;
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('lock-password')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('login-username')), findsOneWidget);
    expect(find.text('Desbloqueio cancelado. Entre com usuário e senha.'), findsOneWidget);
  });

  testWidgets('deep link inválido cai na home', (tester) async {
    await _boot(tester);
    await _login(tester, 'analista');
    _router(tester).go('cactusguard://alerts/nao-e-uuid');
    await tester.pumpAndSettle();
    expect(_path(tester), '/alerts');
  });
}
