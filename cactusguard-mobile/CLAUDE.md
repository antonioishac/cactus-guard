# CactusGuard Mobile (Flutter)

App de analistas/supervisores de compliance. Pacote `br.com.cactus.guard.mobile`. Specs: 024 (escopo + **segurança mobile**),
032 (alertas/notificações), 033 (contratos), 035 (identidade), 022 (auth). Sem regra de negócio no app: o backend decide.

## Comandos (git bash, Flutter 3.44 / Dart 3.12 em /c/flutter/bin)
- Verificação: `flutter analyze; echo EXIT=$?` e `flutter test; echo EXIT=$?` (não confie em `| tail`).
- Mock (sem servidor): `flutter run --dart-define=USE_MOCK=true` — usuários `analista|supervisor|supervisor2|auditor` / `dev12345`, `admin` / `admin123`.
- Emulador Android + backend local: `flutter run` (padrão `API_URL=http://10.0.2.2:8080`).
- Aparelho físico: `flutter run --dart-define=API_URL=https://<host>` (release exige HTTPS; em debug, HTTP só para
  10.0.2.2/localhost — para IP de LAN use `adb reverse tcp:8080 tcp:8080` + `API_URL=http://localhost:8080`).
- Preview web: `flutter run -d chrome --dart-define=USE_MOCK=true`.
- Outros defines: `IDLE_TIMEOUT_MIN` (15), `BIOMETRIC_LOCK_SECONDS` (30), `PUSH_MODE=noop|local`, `APP_VERSION`.
- Ícones: `flutter test tool/generate_icons.dart && dart run flutter_launcher_icons`.

## Estrutura
```
lib/core/  api/ (Dio, AuthInterceptor, ApiError, mock/FakeBackend)  auth/ (SessionStore, AuthController, Role, permissions)
           config/ (AppConfig)  router/ (GoRouter, resolveRedirect, Routes)  security/ (SecureStore, AppLock, InactivityMonitor, SecurityGate)
           push/ (PushService, PushPayload, DeepLink, DeviceRegistrationApi)  theme/  widgets/  utils/ (Fmt, Mask, Labels, AppLogger)
           providers.dart  ← raiz de composição Riverpod (sem codegen)
lib/features/<modulo>/{data,domain,presentation}   (alerts, cases, approvals, notifications, profile, auth, home)
```

## Padrão de feature (próximas trilhas)
- `domain/`: modelos imutáveis com `fromJson` manual (tipos de referência: `cactusguard-web/src/api/types/*`).
- `data/`: `XxxRepository(Dio dio)` usando **`ref.watch(dioProvider)`** (já tem JWT + refresh em fila). Em `catch`, converta com
  `ApiError.from(e)` e mostre `e.userMessage` (inclui `ref. traceId`). Provider: `Provider((ref) => XxxRepository(ref.watch(dioProvider)))`.
- `presentation/`: `FutureProvider`/`AsyncNotifier` + `AsyncValue.when` com `SkeletonList`/`LoadingState`, `ErrorState(onRetry)`, `EmptyState`.
  Telas usam `AppScaffold`; badges `StatusBadge.severity/status`; documentos via `MaskedText`; valores/datas via `Fmt`.
- Ações: botão só aparece se `can(roles, AppAction.x)` (conveniência; backend é a barreira). Ação regulatória → `showJustificationDialog`
  (≥ 20 não brancos) → chamada → `AppSnack.success/error`. Escalar não é decisão; quatro olhos é do backend (403 ao próprio decisor).
- Rotas: placeholders em `core/router/app_router.dart` (`/alerts/:id`, `/cases/:id`, `/approvals` só SUPERVISOR via `routeRoles`).
  Troque os widgets placeholder; não mude os paths (deep links/push dependem deles).
- Backend fake: adicione endpoints em `core/api/mock/fake_backend.dart`/`fake_data.dart` (dados fictícios, documentos já mascarados).
- Testes: `test/helpers.dart` (`TestEnv`: FakeBackend latência zero, `MemorySecureStore`, biometria e push falsos).

## Segurança (não regredir)
- Tokens: access só em memória; refresh no `flutter_secure_storage` (Keystore/Keychain), rotacionado a cada refresh. Nunca SharedPreferences.
- `FLAG_SECURE` + `setRecentsScreenshotEnabled(false)` (MainActivity); iOS cobre a janela ao sair do foreground (SceneDelegate).
- `allowBackup=false` + `data_extraction_rules`; `network_security_config` HTTPS-only (overlay debug libera 10.0.2.2/localhost).
  O HttpClient do Dart ignora esse XML: `AppConfig.validate()` recusa `API_URL` sem HTTPS em release.
- Logs: só `AppLogger` (silencioso em release, redige CPF/CNPJ/JWT); `SafeLogInterceptor` loga método/caminho/status, nunca corpo.
  Proibido `print`/`LogInterceptor` do Dio.
- Push: payload **somente** `{type: ALERT|CASE|SLA, id: uuid}` (`PushPayload.tryParse` rejeita qualquer campo extra); texto genérico.
  `SLA` refere-se a case (`/cases/{id}`).

## Push / FCM (desligado até existir projeto Firebase)
Hoje: `NoopPushService` (padrão) ou `LocalSimulatedPushService` (`PUSH_MODE=local`, botão "Simular push" em Notificações).
Registro: login → `POST /devices {token, platform, appVersion}`; logout → `DELETE /devices/{token}`; preferências `GET/PUT /devices/preferences`.
Para ligar o FCM:
1. `flutter pub add firebase_core firebase_messaging`; `flutterfire configure` (gera `firebase_options.dart`) ou copie
   `google-services.json` → `android/app/` e `GoogleService-Info.plist` → `ios/Runner/` (**nunca versionar**; já no .gitignore).
2. Android: plugin `com.google.gms.google-services` em `settings.gradle.kts`/`app/build.gradle.kts`. iOS: capability Push + APNs key no Firebase.
3. Criar `core/push/fcm_push_service.dart` implementando `PushService`: `getToken()` = `FirebaseMessaging.instance.getToken()`;
   `onMessage`/`onMessageOpenedApp`/`getInitialMessage` → `PushPayload.tryParse(message.data)`; em foreground exibir via
   flutter_local_notifications com `PushType.genericText`; `onTokenRefresh` → `DeviceRegistrationApi.register`.
4. `Firebase.initializeApp()` no `main.dart` e trocar o `case` em `pushServiceProvider` (ex.: `PUSH_MODE=fcm`).
5. Backend envia **data message** só com `{type,id}` e notification genérica (sem nome/documento/valor).

## Deep links
`cactusguard://alerts/{id}` e `cactusguard://cases/{id}` (intent-filter Android, `CFBundleURLTypes` iOS, `flutter_deeplinking_enabled`).
Convertidos em `resolveRedirect` (`core/router/route_guard.dart`); sem sessão passam por login/biometria e voltam ao destino (`?from=`).
Teste manual: `adb shell am start -a android.intent.action.VIEW -d "cactusguard://alerts/<uuid>" br.com.cactus.guard.mobile`.
