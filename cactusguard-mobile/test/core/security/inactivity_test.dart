import 'package:cactusguard_mobile/core/security/app_lock.dart';
import 'package:cactusguard_mobile/core/security/biometric_service.dart';
import 'package:cactusguard_mobile/core/security/inactivity_monitor.dart';
import 'package:cactusguard_mobile/core/security/secure_store.dart';
import 'package:cactusguard_mobile/core/utils/logger.dart';
import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  setUpAll(() => AppLogger.sink = (_) {});

  group('timeout de inatividade', () {
    test('expira após 15 min sem interação', () {
      fakeAsync((fa) {
        var fired = 0;
        final m = InactivityMonitor(timeout: const Duration(minutes: 15), onTimeout: () => fired++, clock: () => fa.getClock(DateTime(2026)).now())
          ..start();
        fa.elapse(const Duration(minutes: 14, seconds: 59));
        expect(fired, 0);
        fa.elapse(const Duration(seconds: 1));
        expect(fired, 1);
        expect(m.isRunning, isFalse);
      });
    });

    test('interação reinicia a contagem', () {
      fakeAsync((fa) {
        var fired = 0;
        final m = InactivityMonitor(timeout: const Duration(minutes: 15), onTimeout: () => fired++)..start();
        fa.elapse(const Duration(minutes: 10));
        m.touch();
        fa.elapse(const Duration(minutes: 10));
        expect(fired, 0);
        fa.elapse(const Duration(minutes: 5));
        expect(fired, 1);
      });
    });

    test('ao voltar do background compara o relógio (timers podem não rodar em background)', () {
      var now = DateTime(2026, 11, 8, 10);
      var fired = 0;
      final m = InactivityMonitor(timeout: const Duration(minutes: 15), onTimeout: () => fired++, clock: () => now)..start();
      now = now.add(const Duration(minutes: 16));
      expect(m.checkElapsed(), isTrue);
      expect(fired, 1);
      m.dispose();
    });

    test('parado não dispara', () {
      fakeAsync((fa) {
        var fired = 0;
        InactivityMonitor(timeout: const Duration(minutes: 1), onTimeout: () => fired++)
          ..start()
          ..stop();
        fa.elapse(const Duration(minutes: 5));
        expect(fired, 0);
      });
    });
  });

  group('bloqueio biométrico', () {
    late DateTime now;
    late FakeBiometricService bio;
    late AppLockController lock;

    setUp(() async {
      now = DateTime(2026, 11, 8, 10);
      bio = FakeBiometricService();
      lock = AppLockController(lockAfter: const Duration(seconds: 30), biometrics: bio, storage: MemorySecureStore(), clock: () => now);
      expect(await lock.setEnabled(true), isTrue);
    });

    test('bloqueia ao voltar após N segundos em background', () {
      lock.onBackground();
      now = now.add(const Duration(seconds: 31));
      lock.onForeground(authenticated: true);
      expect(lock.locked, isTrue);
    });

    test('não bloqueia se voltou antes do limite, sem sessão ou com biometria desativada', () async {
      lock.onBackground();
      now = now.add(const Duration(seconds: 10));
      lock.onForeground(authenticated: true);
      expect(lock.locked, isFalse);

      lock.onBackground();
      now = now.add(const Duration(minutes: 5));
      lock.onForeground(authenticated: false);
      expect(lock.locked, isFalse);

      await lock.setEnabled(false);
      lock.onBackground();
      now = now.add(const Duration(minutes: 5));
      lock.onForeground(authenticated: true);
      expect(lock.locked, isFalse);
    });

    test('desbloqueio exige biometria bem-sucedida', () async {
      lock.onBackground();
      now = now.add(const Duration(minutes: 1));
      lock.onForeground(authenticated: true);
      bio.result = false;
      expect(await lock.unlock(), isFalse);
      expect(lock.locked, isTrue);
      bio.result = true;
      expect(await lock.unlock(), isTrue);
      expect(lock.locked, isFalse);
    });

    test('ativar biometria falha se o aparelho não suporta', () async {
      bio.available = false;
      await lock.setEnabled(false);
      expect(await lock.setEnabled(true), isFalse);
      expect(lock.biometricEnabled, isFalse);
    });
  });
}
