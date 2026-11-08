import 'package:cactusguard_mobile/core/api/api_error.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

DioException _err(int status, Object? data, {Map<String, List<String>> headers = const {}}) {
  final ro = RequestOptions(path: '/x');
  return DioException(
    requestOptions: ro,
    type: DioExceptionType.badResponse,
    response: Response(requestOptions: ro, statusCode: status, data: data, headers: Headers.fromMap(headers)),
  );
}

void main() {
  test('erro de negócio usa a mensagem do backend + code + traceId', () {
    final e = ApiError.from(_err(422, {'code': 'CASE_ALREADY_CLOSED', 'message': 'Case já encerrado', 'traceId': 'abc123'}));
    expect(e.code, 'CASE_ALREADY_CLOSED');
    expect(e.message, 'Case já encerrado');
    expect(e.userMessage, 'Case já encerrado (ref. abc123)');
  });

  test('códigos técnicos conhecidos têm texto fixo em pt-BR', () {
    expect(ApiError.from(_err(403, {'code': 'FORBIDDEN', 'message': 'Acesso negado'})).message,
        'Você não tem permissão para esta ação.');
  });

  test('5xx nunca expõe a mensagem do servidor; traceId do header', () {
    final e = ApiError.from(_err(500, {'code': 'INTERNAL_ERROR', 'message': 'Internal error'},
        headers: {'x-trace-id': ['t-1']}));
    expect(e.message, 'Erro interno. Tente novamente em instantes.');
    expect(e.traceId, 't-1');
  });

  test('sem corpo: fallback por status', () {
    expect(ApiError.from(_err(409, null)).message, 'Conflito com o estado atual do registro.');
  });

  test('sem resposta: rede / timeout', () {
    final ro = RequestOptions(path: '/x');
    expect(ApiError.from(DioException(requestOptions: ro, type: DioExceptionType.connectionError)).code, 'NETWORK');
    expect(ApiError.from(DioException(requestOptions: ro, type: DioExceptionType.receiveTimeout)).code, 'TIMEOUT');
    expect(ApiError.from(StateError('x')).code, 'UNKNOWN');
  });
}
