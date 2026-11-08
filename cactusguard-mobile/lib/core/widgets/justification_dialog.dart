import 'package:flutter/material.dart';

/// Mínimo de caracteres da justificativa (alinhado ao backend: "≥ 20 não brancos").
const kMinJustification = 20;
const kMaxJustification = 2000;

/// Conta caracteres que não são espaço em branco (critério conservador, igual ao backend).
int justificationLength(String text) => text.replaceAll(RegExp(r'\s'), '').length;

/// Diálogo de confirmação com **justificativa obrigatória** (decisão de case, aprovação, falso
/// positivo...). Retorna o texto (trim) ou `null` se cancelado. A regra continua no backend.
Future<String?> showJustificationDialog(
  BuildContext context, {
  required String title,
  String? message,
  String confirmLabel = 'Confirmar',
  int minLength = kMinJustification,
  String hint = 'Descreva o fundamento da ação',
}) {
  return showDialog<String>(
    context: context,
    barrierDismissible: false,
    builder: (_) => JustificationDialog(
      title: title,
      message: message,
      confirmLabel: confirmLabel,
      minLength: minLength,
      hint: hint,
    ),
  );
}

class JustificationDialog extends StatefulWidget {
  const JustificationDialog({
    super.key,
    required this.title,
    this.message,
    this.confirmLabel = 'Confirmar',
    this.minLength = kMinJustification,
    this.hint = 'Descreva o fundamento da ação',
  });

  final String title;
  final String? message;
  final String confirmLabel;
  final int minLength;
  final String hint;

  @override
  State<JustificationDialog> createState() => _JustificationDialogState();
}

class _JustificationDialogState extends State<JustificationDialog> {
  final _controller = TextEditingController();

  @override
  void initState() {
    super.initState();
    _controller.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  int get _count => justificationLength(_controller.text);
  bool get _valid => _count >= widget.minLength;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final remaining = widget.minLength - _count;
    return AlertDialog(
      title: Text(widget.title),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (widget.message != null) ...[Text(widget.message!), const SizedBox(height: 12)],
            TextField(
              key: const Key('justification-field'),
              controller: _controller,
              autofocus: true,
              minLines: 3,
              maxLines: 6,
              maxLength: kMaxJustification,
              textCapitalization: TextCapitalization.sentences,
              decoration: InputDecoration(
                labelText: 'Justificativa (obrigatória)',
                hintText: widget.hint,
                counterText: '',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              _valid
                  ? '$_count caracteres'
                  : '$_count/${widget.minLength} — faltam $remaining caractere${remaining == 1 ? '' : 's'}',
              key: const Key('justification-counter'),
              style: t.textTheme.bodySmall?.copyWith(color: _valid ? t.colorScheme.primary : t.colorScheme.error),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancelar')),
        FilledButton(
          key: const Key('justification-confirm'),
          style: FilledButton.styleFrom(minimumSize: const Size(88, 40)),
          onPressed: _valid ? () => Navigator.of(context).pop(_controller.text.trim()) : null,
          child: Text(widget.confirmLabel),
        ),
      ],
    );
  }
}
