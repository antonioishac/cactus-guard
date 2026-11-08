import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:intl/intl.dart';

import 'app.dart';
import 'core/config/app_config.dart';
import 'core/providers.dart';
import 'core/security/secure_store.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  Intl.defaultLocale = 'pt_BR';
  await initializeDateFormatting('pt_BR');

  final config = AppConfig.fromEnvironment()..validate();

  runApp(ProviderScope(
    overrides: [
      appConfigProvider.overrideWithValue(config),
      // Preview web em modo mock: sem Keystore/Keychain, mantém tudo só em memória.
      if (kIsWeb) secureStoreProvider.overrideWithValue(MemorySecureStore()),
    ],
    child: const CactusGuardApp(),
  ));
}
