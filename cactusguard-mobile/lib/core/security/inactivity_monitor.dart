import 'dart:async';

/// Timeout de sessão por inatividade (padrão 15 min, igual ao web).
///
/// - Em primeiro plano: um [Timer] reiniciado a cada interação ([touch]).
/// - Em background timers não são confiáveis: ao voltar, [checkElapsed] compara o relógio com a
///   última atividade e dispara [onTimeout] se o limite passou.
class InactivityMonitor {
  InactivityMonitor({required this.timeout, required this.onTimeout, DateTime Function()? clock})
      : _clock = clock ?? DateTime.now;

  final Duration timeout;
  final void Function() onTimeout;
  final DateTime Function() _clock;

  Timer? _timer;
  DateTime? _lastActivity;
  bool _running = false;

  bool get isRunning => _running;
  DateTime? get lastActivity => _lastActivity;

  void start() {
    _running = true;
    touch();
  }

  void stop() {
    _running = false;
    _timer?.cancel();
    _timer = null;
  }

  /// Registra interação do usuário e reinicia a contagem.
  void touch() {
    if (!_running) return;
    _lastActivity = _clock();
    _timer?.cancel();
    _timer = Timer(timeout, _fire);
  }

  /// Chamado ao voltar do background. Retorna true se expirou (e já disparou [onTimeout]).
  bool checkElapsed() {
    final last = _lastActivity;
    if (!_running || last == null) return false;
    if (_clock().difference(last) >= timeout) {
      _fire();
      return true;
    }
    return false;
  }

  void _fire() {
    if (!_running) return;
    stop();
    onTimeout();
  }

  void dispose() => stop();
}
