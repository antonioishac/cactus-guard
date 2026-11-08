import 'package:dio/dio.dart';

/// Erro padronizado da API: `{timestamp, status, error, message, code, traceId}` do
/// GlobalExceptionHandler do backend (traceId também no header `X-Trace-Id`).
class ApiError implements Exception {
  const ApiError({required this.status, required this.code, required this.message, this.traceId});

  /// 0 = sem resposta (rede/timeout).
  final int status;
  final String code;

  /// Mensagem amigável em pt-BR, pronta para a UI.
  final String message;
  final String? traceId;

  /// Mensagem com referência para o suporte correlacionar com os logs do backend.
  String get userMessage => traceId == null || traceId!.isEmpty ? message : '$message (ref. $traceId)';

  bool get isUnauthorized => status == 401;
  bool get isForbidden => status == 403;
  bool get isNetwork => status == 0;

  static const _byCode = {
    'NETWORK': 'Sem conexão com o servidor. Verifique sua internet.',
    'TIMEOUT': 'O servidor demorou para responder. Tente novamente.',
    'UNAUTHORIZED': 'Usuário ou senha inválidos.',
    'FORBIDDEN': 'Você não tem permissão para esta ação.',
    'NOT_FOUND': 'Registro não encontrado.',
    'METHOD_NOT_ALLOWED': 'Operação não suportada.',
    'PAYLOAD_TOO_LARGE': 'Arquivo ou conteúdo grande demais.',
    'INTERNAL_ERROR': 'Erro interno. Tente novamente em instantes.',
    'SESSION_EXPIRED': 'Sua sessão expirou. Entre novamente.',
  };

  static const _byStatus = {
    400: 'Requisição inválida.',
    401: 'Sessão expirada ou credenciais inválidas.',
    403: 'Você não tem permissão para esta ação.',
    404: 'Registro não encontrado.',
    409: 'Conflito com o estado atual do registro.',
    422: 'Não foi possível concluir: regra de negócio não atendida.',
    429: 'Muitas requisições. Aguarde alguns instantes.',
  };

  /// Converte qualquer erro em [ApiError]. Nunca inclui corpo da requisição (pode ter PII).
  ///
  /// Mensagem: códigos técnicos conhecidos e 5xx usam texto fixo em pt-BR; erros de negócio/validação
  /// (422/400 com `code` próprio) usam a `message` do backend, que é escrita para o usuário.
  static ApiError from(Object error) {
    if (error is ApiError) return error;
    if (error is DioException) {
      if (error.error is ApiError) return error.error! as ApiError;
      final res = error.response;
      if (res == null) {
        final timeout = error.type == DioExceptionType.connectionTimeout ||
            error.type == DioExceptionType.receiveTimeout ||
            error.type == DioExceptionType.sendTimeout;
        final code = timeout ? 'TIMEOUT' : 'NETWORK';
        return ApiError(status: 0, code: code, message: _byCode[code]!);
      }
      final status = res.statusCode ?? 0;
      final body = res.data is Map ? (res.data as Map).cast<String, dynamic>() : const <String, dynamic>{};
      final code = (body['code'] as String?) ?? (body['error'] as String?) ?? 'HTTP_$status';
      final traceId = (body['traceId'] as String?) ?? res.headers.value('x-trace-id');
      final backendMsg = (body['message'] as String?)?.trim();
      final String message;
      if (status >= 500) {
        message = _byCode['INTERNAL_ERROR']!;
      } else if (_byCode.containsKey(code)) {
        message = _byCode[code]!;
      } else if (backendMsg != null && backendMsg.isNotEmpty) {
        message = backendMsg;
      } else {
        message = _byStatus[status] ?? 'Erro $status.';
      }
      return ApiError(status: status, code: code, message: message, traceId: traceId);
    }
    return const ApiError(status: 0, code: 'UNKNOWN', message: 'Erro inesperado.');
  }

  @override
  String toString() => 'ApiError($status, $code)';
}
