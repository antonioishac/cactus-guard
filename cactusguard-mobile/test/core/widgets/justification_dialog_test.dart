import 'package:cactusguard_mobile/core/widgets/justification_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('confirmar fica desabilitado até 20 caracteres não brancos e mostra contador', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: Builder(
        builder: (context) => TextButton(
          onPressed: () => showJustificationDialog(context, title: 'Arquivar case', confirmLabel: 'Arquivar'),
          child: const Text('abrir'),
        ),
      ),
    ));
    await tester.tap(find.text('abrir'));
    await tester.pumpAndSettle();
    FilledButton confirm() => tester.widget<FilledButton>(find.byKey(const Key('justification-confirm')));

    expect(confirm().onPressed, isNull);
    expect(find.text('0/20 — faltam 20 caracteres'), findsOneWidget);

    // espaços não contam
    await tester.enterText(find.byKey(const Key('justification-field')), 'a b c d e f g h i j k l m n o p q r s');
    await tester.pump();
    expect(confirm().onPressed, isNull);
    expect(find.text('19/20 — faltam 1 caractere'), findsOneWidget);

    await tester.enterText(find.byKey(const Key('justification-field')), '  Sem indícios após análise documental  ');
    await tester.pump();
    expect(confirm().onPressed, isNotNull);
  });

  testWidgets('retorna a justificativa (trim) ao confirmar e null ao cancelar', (tester) async {
    String? result = 'não chamado';
    await tester.pumpWidget(MaterialApp(
      home: Builder(
        builder: (context) => TextButton(
          onPressed: () async => result = await showJustificationDialog(context, title: 'Decidir'),
          child: const Text('abrir'),
        ),
      ),
    ));

    await tester.tap(find.text('abrir'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('justification-field')), '  Operação compatível com a renda declarada  ');
    await tester.pump();
    await tester.tap(find.byKey(const Key('justification-confirm')));
    await tester.pumpAndSettle();
    expect(result, 'Operação compatível com a renda declarada');

    await tester.tap(find.text('abrir'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();
    expect(result, isNull);
  });

  test('contagem ignora espaços', () {
    expect(justificationLength('  a b\n c\t'), 3);
  });
}
