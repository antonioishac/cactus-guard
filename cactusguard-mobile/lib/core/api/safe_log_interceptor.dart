import 'package:dio/dio.dart';

import '../utils/logger.dart';

/// Log de rede seguro: só método, caminho (sem query — pode conter documento), status e duração.
/// **Nunca** registra corpo, headers ou tokens. Em release o [AppLogger] não imprime nada.
class SafeLogInterceptor extends Interceptor {
  static const _startKey = 'cg.start';

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    options.extra[_startKey] = DateTime.now().millisecondsSinceEpoch;
    handler.next(options);
  }

  @override
  void onResponse(Response<dynamic> response, ResponseInterceptorHandler handler) {
    _log(response.requestOptions, response.statusCode);
    handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    _log(err.requestOptions, err.response?.statusCode, type: err.type.name);
    handler.next(err);
  }

  void _log(RequestOptions o, int? status, {String? type}) {
    if (!AppLogger.enabled) return;
    final start = o.extra[_startKey] as int?;
    final ms = start == null ? '?' : '${DateTime.now().millisecondsSinceEpoch - start}';
    final path = _maskIds(o.uri.path);
    AppLogger.debug('${o.method} $path -> ${status ?? type ?? '-'} (${ms}ms)');
  }

  static final _uuid = RegExp(r'[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}');

  /// Ids e tokens no caminho (ex.: DELETE /devices/{token}) viram marcadores.
  static String _maskIds(String path) => path
      .replaceAll(_uuid, ':id')
      .replaceAllMapped(RegExp(r'/devices/[^/]+'), (_) => '/devices/:token');
}
