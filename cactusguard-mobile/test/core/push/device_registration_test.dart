import 'package:cactusguard_mobile/core/api/api_error.dart';
import 'package:cactusguard_mobile/core/providers.dart';
import 'package:cactusguard_mobile/core/push/device_registration.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../helpers.dart';

void main() {
  late TestEnv env;
  late ProviderContainer c;

  setUp(() async {
    env = TestEnv();
    c = env.container();
    await c.read(authControllerProvider).login('supervisor2', 'dev12345');
  });

  tearDown(() => c.dispose());

  test('preferências de push: GET padrão e PUT persistido', () async {
    final api = c.read(deviceRegistrationProvider);
    final initial = await api.getPreferences();
    expect(initial.toJson(), {'criticalAlerts': true, 'deadlines': true});

    final updated = await api.updatePreferences(initial.copyWith(deadlines: false));
    expect(updated.deadlines, isFalse);
    expect((await api.getPreferences()).toJson(), {'criticalAlerts': true, 'deadlines': false});
  });

  test('sem sessão, preferências falham com ApiError 401', () async {
    await c.read(authControllerProvider).logout();
    final api = c.read(deviceRegistrationProvider);
    try {
      await api.getPreferences();
      fail('deveria lançar');
    } catch (e) {
      expect(ApiError.from(e).status, 401);
    }
  });

  test('admin usa senha própria', () async {
    await c.read(authControllerProvider).logout();
    await c.read(authControllerProvider).login('admin', 'admin123');
    expect(c.read(sessionStoreProvider).isAuthenticated, isTrue);
  });

  test('PushPreferences tolera campos ausentes', () {
    expect(PushPreferences.fromJson(const {}).criticalAlerts, isTrue);
  });
}
