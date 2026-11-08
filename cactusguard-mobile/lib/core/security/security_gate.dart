import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../auth/auth_models.dart';
import '../auth/session_store.dart';
import '../providers.dart';
import '../push/push_payload.dart';
import 'inactivity_monitor.dart';

/// Envolve o app (MaterialApp.router `builder`) e aplica:
/// - timeout de inatividade (toques/rolagem reiniciam; ao voltar do background compara o relógio);
/// - bloqueio biométrico ao voltar do background;
/// - abertura de notificações push → navegação para o detalhe.
class SecurityGate extends ConsumerStatefulWidget {
  const SecurityGate({super.key, required this.router, required this.child});

  final GoRouter router;
  final Widget child;

  @override
  ConsumerState<SecurityGate> createState() => _SecurityGateState();
}

class _SecurityGateState extends ConsumerState<SecurityGate> with WidgetsBindingObserver {
  late final InactivityMonitor _monitor;
  late final SessionStore _store;
  StreamSubscription<PushPayload>? _pushSub;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    final config = ref.read(appConfigProvider);
    _monitor = InactivityMonitor(
      timeout: config.idleTimeout,
      onTimeout: () => ref.read(authControllerProvider).logout(ClearReason.idle),
    );
    _store = ref.read(sessionStoreProvider)..addListener(_syncMonitor);
    _syncMonitor();
    _initPush();
  }

  Future<void> _initPush() async {
    final push = ref.read(pushServiceProvider);
    try {
      await push.init();
      _pushSub = push.onOpened.listen(_openPayload);
      final initial = await push.initialPayload();
      if (initial != null) _openPayload(initial);
    } catch (_) {
      // push é opcional; nunca impede o app de abrir
    }
  }

  /// O redirect do router cuida de login/bloqueio e devolve ao destino depois (`?from=`).
  void _openPayload(PushPayload p) {
    if (mounted) widget.router.go(p.location);
  }

  void _syncMonitor() {
    final authenticated = _store.isAuthenticated;
    if (authenticated && !_monitor.isRunning) {
      _monitor.start();
    } else if (!authenticated && _monitor.isRunning) {
      _monitor.stop();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final lock = ref.read(appLockProvider);
    final store = _store;
    switch (state) {
      case AppLifecycleState.paused:
      case AppLifecycleState.hidden:
        lock.onBackground();
      case AppLifecycleState.resumed:
        if (_monitor.checkElapsed()) {
          lock.reset();
          return;
        }
        lock.onForeground(authenticated: store.isAuthenticated);
        _monitor.touch();
      case AppLifecycleState.inactive:
      case AppLifecycleState.detached:
        break;
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _store.removeListener(_syncMonitor);
    _pushSub?.cancel();
    _monitor.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: (_) => _monitor.touch(),
      onPointerSignal: (_) => _monitor.touch(),
      child: widget.child,
    );
  }
}
