import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import '../utils/logger.dart';
import 'push_payload.dart';

/// Abstração de push. Hoje: [NoopPushService] (padrão) e [LocalSimulatedPushService] (dev).
/// Quando houver projeto Firebase, criar `FcmPushService` implementando esta interface
/// (passo a passo em `cactusguard-mobile/CLAUDE.md`).
abstract interface class PushService {
  /// Inicializa o canal e pede permissão quando aplicável.
  Future<void> init();

  /// Token do aparelho para `POST /api/v1/devices`; `null` = não registrar.
  Future<String?> getToken();

  /// Mensagens recebidas com o app em primeiro plano (já validadas).
  Stream<PushPayload> get onMessage;

  /// Notificação tocada pelo usuário → o app navega para [PushPayload.location].
  Stream<PushPayload> get onOpened;

  /// Payload que abriu o app a frio (se houver).
  Future<PushPayload?> initialPayload();

  Future<void> dispose();
}

/// Push desligado (padrão até existir Firebase — spec 024, decisões).
class NoopPushService implements PushService {
  @override
  Future<void> init() async {}

  @override
  Future<String?> getToken() async => null;

  @override
  Stream<PushPayload> get onMessage => const Stream.empty();

  @override
  Stream<PushPayload> get onOpened => const Stream.empty();

  @override
  Future<PushPayload?> initialPayload() async => null;

  @override
  Future<void> dispose() async {}
}

/// Push simulado para desenvolvimento: [simulate] dispara notificação **local** com texto genérico
/// e payload `{type,id}`; o toque abre o deep link. Ative com `--dart-define=PUSH_MODE=local`.
class LocalSimulatedPushService implements PushService {
  LocalSimulatedPushService([FlutterLocalNotificationsPlugin? plugin])
      : _plugin = plugin ?? FlutterLocalNotificationsPlugin();

  final FlutterLocalNotificationsPlugin _plugin;
  final _messages = StreamController<PushPayload>.broadcast();
  final _opened = StreamController<PushPayload>.broadcast();
  String? _token;
  var _nextId = 0;

  static const _channel = AndroidNotificationDetails(
    'cg_alerts',
    'Alertas e prazos',
    channelDescription: 'Novos alertas críticos e prazos de SLA',
    importance: Importance.high,
    priority: Priority.high,
    // Na tela bloqueada mostra só "conteúdo oculto" (nunca o texto completo).
    visibility: NotificationVisibility.private,
  );

  @override
  Future<void> init() async {
    if (kIsWeb) return;
    await _plugin.initialize(
      settings: const InitializationSettings(
        android: AndroidInitializationSettings('@mipmap/ic_launcher'),
        iOS: DarwinInitializationSettings(),
      ),
      onDidReceiveNotificationResponse: (r) => _emitOpened(r.payload),
    );
    await _plugin
        .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
        ?.requestNotificationsPermission();
  }

  void _emitOpened(String? raw) {
    final p = _decode(raw);
    if (p != null) _opened.add(p);
  }

  static PushPayload? _decode(String? raw) {
    if (raw == null || raw.isEmpty) return null;
    try {
      final data = jsonDecode(raw);
      return data is Map ? PushPayload.tryParse(data.cast<String, dynamic>()) : null;
    } catch (_) {
      AppLogger.warn('Payload de notificação local ilegível');
      return null;
    }
  }

  /// Token fictício estável por execução (o backend fake aceita; o real também, sem efeito de envio).
  @override
  Future<String?> getToken() async =>
      _token ??= 'local-sim-${Random.secure().nextInt(1 << 32).toRadixString(16)}';

  @override
  Stream<PushPayload> get onMessage => _messages.stream;

  @override
  Stream<PushPayload> get onOpened => _opened.stream;

  @override
  Future<PushPayload?> initialPayload() async {
    if (kIsWeb) return null;
    final details = await _plugin.getNotificationAppLaunchDetails();
    if (details?.didNotificationLaunchApp != true) return null;
    return _decode(details!.notificationResponse?.payload);
  }

  /// Simula a chegada de um push. Recebe o `data` bruto para passar pela mesma validação do FCM.
  Future<bool> simulate(Map<String, dynamic> data) async {
    final p = PushPayload.tryParse(data);
    if (p == null) return false;
    _messages.add(p);
    if (!kIsWeb) {
      await _plugin.show(
        id: _nextId++,
        title: 'CactusGuard',
        body: p.type.genericText,
        notificationDetails: const NotificationDetails(
          android: _channel,
          iOS: DarwinNotificationDetails(),
        ),
        payload: jsonEncode(p.toData()),
      );
    }
    return true;
  }

  @override
  Future<void> dispose() async {
    await _messages.close();
    await _opened.close();
  }
}
