import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/alerts/presentation/alerts_page.dart';
import '../../features/approvals/presentation/approvals_page.dart';
import '../../features/auth/presentation/lock_page.dart';
import '../../features/auth/presentation/login_page.dart';
import '../../features/auth/presentation/splash_page.dart';
import '../../features/auth/presentation/unauthorized_page.dart';
import '../../features/cases/presentation/cases_page.dart';
import '../../features/home/presentation/home_shell.dart';
import '../../features/notifications/presentation/notifications_page.dart';
import '../../features/profile/presentation/profile_page.dart';
import '../providers.dart';
import 'route_guard.dart';

final rootNavigatorKey = GlobalKey<NavigatorState>(debugLabel: 'root');

/// Índices dos ramos do shell (ordem da bottom navigation).
abstract final class ShellBranch {
  static const alerts = 0;
  static const cases = 1;
  static const approvals = 2;
  static const notifications = 3;
  static const profile = 4;
}

final routerProvider = Provider<GoRouter>((ref) {
  final store = ref.watch(sessionStoreProvider);
  final lock = ref.watch(appLockProvider);
  final router = GoRouter(
    navigatorKey: rootNavigatorKey,
    initialLocation: Routes.splash,
    refreshListenable: Listenable.merge([store, lock]),
    redirect: (context, state) => resolveRedirect(
      uri: state.uri,
      status: store.status,
      roles: store.roles,
      locked: lock.locked,
    ),
    errorBuilder: (context, state) => const UnauthorizedPage(notFound: true),
    routes: [
      GoRoute(path: '/', redirect: (_, _) => Routes.home),
      GoRoute(path: Routes.splash, builder: (_, _) => const SplashPage()),
      GoRoute(path: Routes.login, builder: (_, _) => const LoginPage()),
      GoRoute(path: Routes.lock, builder: (_, _) => const LockPage()),
      GoRoute(path: Routes.unauthorized, builder: (_, _) => const UnauthorizedPage()),
      StatefulShellRoute.indexedStack(
        builder: (context, state, shell) => HomeShell(navigationShell: shell),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(
              path: Routes.alerts,
              builder: (_, _) => const AlertsPage(),
              routes: [
                GoRoute(path: ':id', builder: (_, s) => AlertDetailPage(id: s.pathParameters['id']!)),
              ],
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: Routes.cases,
              builder: (_, _) => const CasesPage(),
              routes: [
                GoRoute(path: ':id', builder: (_, s) => CaseDetailPage(id: s.pathParameters['id']!)),
              ],
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: Routes.approvals, builder: (_, _) => const ApprovalsPage()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: Routes.notifications, builder: (_, _) => const NotificationsPage()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: Routes.profile, builder: (_, _) => const ProfilePage()),
          ]),
        ],
      ),
    ],
  );
  ref.onDispose(router.dispose);
  return router;
});
