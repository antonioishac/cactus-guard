import 'package:dio/dio.dart';

import '../auth/session_store.dart';

/// Interceptor JWT com refresh automático em 401 (fila única — ver [SessionStore.refresh]).
///
/// - Antes de enviar: se há refresh em curso, a requisição espera e sai já com o token novo.
/// - Em 401: se o token usado já foi trocado por outro refresh, só repete; senão dispara (ou se junta
///   ao) refresh e repete uma única vez. Falha no refresh → sessão encerrada pelo [SessionStore].
class AuthInterceptor extends Interceptor {
  AuthInterceptor({required this.session, required this.dio});

  final SessionStore session;

  /// Dio usado para repetir a requisição original (o mesmo que contém este interceptor).
  final Dio dio;

  static const _retriedKey = 'cg.retried';
  static const _authPaths = ['/auth/login', '/auth/refresh', '/auth/logout'];

  static bool _isAuthPath(String path) => _authPaths.any(path.contains);

  @override
  Future<void> onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    if (_isAuthPath(options.path)) return handler.next(options);
    final pending = session.pendingRefresh;
    if (pending != null) {
      try {
        await pending;
      } catch (_) {
        // segue; o 401 será tratado em onError
      }
    }
    final token = session.accessToken;
    if (token != null) options.headers['Authorization'] = 'Bearer $token';
    handler.next(options);
  }

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    final options = err.requestOptions;
    final is401 = err.response?.statusCode == 401;
    if (!is401 || options.extra[_retriedKey] == true || _isAuthPath(options.path)) {
      return handler.next(err);
    }
    final String token;
    try {
      final used = options.headers['Authorization'];
      final current = session.accessToken;
      // Outro 401 já renovou a sessão enquanto esta requisição voava: só repete com o token atual.
      token = (current != null && used != 'Bearer $current') ? current : await session.refresh();
    } catch (_) {
      // refresh falhou: devolve o 401 original (a sessão já foi encerrada pelo SessionStore)
      return handler.next(err);
    }
    try {
      options.extra[_retriedKey] = true;
      options.headers['Authorization'] = 'Bearer $token';
      handler.resolve(await dio.fetch<dynamic>(options));
    } on DioException catch (e) {
      handler.next(e);
    }
  }
}
