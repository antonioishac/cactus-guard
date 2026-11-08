// Gera os PNGs do logo a partir da mesma geometria de assets/logo/cactusguard-logo.svg (spec 035).
// Uso (não faz parte da suíte; roda no ambiente do flutter_test por causa do dart:ui):
//   flutter test tool/generate_icons.dart
//   dart run flutter_launcher_icons
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

const _accent = Color(0xFF00C9A7);
const _gold = Color(0xFFC9A227);
const _neutral = Color(0xFF404040);

/// Desenha o símbolo (viewBox 48x48) ocupando [box] centralizado em [size].
void _paintMark(Canvas canvas, double size, double box) {
  final s = box / 48;
  final o = (size - box) / 2;
  Offset p(double x, double y) => Offset(o + x * s, o + y * s);
  Paint stroke(Color c) => Paint()
    ..color = c
    ..style = PaintingStyle.stroke
    ..strokeWidth = 7 * s
    ..strokeCap = StrokeCap.round
    ..isAntiAlias = true;
  Path cubic(Offset a, Offset c1, Offset c2, Offset b) =>
      Path()..moveTo(a.dx, a.dy)..cubicTo(c1.dx, c1.dy, c2.dx, c2.dy, b.dx, b.dy);

  canvas.drawPath(cubic(p(24, 8), p(12, 8), p(8, 20), p(14, 30)), stroke(_accent));
  canvas.drawPath(cubic(p(40, 30), p(40, 18), p(32, 12), p(24, 8)), stroke(_gold));
  canvas.drawPath(cubic(p(14, 38), p(22, 44), p(36, 42), p(40, 30)), stroke(_neutral));
}

Future<void> _write(String path, int size, double boxFactor, {Color? background}) async {
  final recorder = ui.PictureRecorder();
  final canvas = Canvas(recorder);
  final d = size.toDouble();
  if (background != null) canvas.drawRect(Rect.fromLTWH(0, 0, d, d), Paint()..color = background);
  _paintMark(canvas, d, d * boxFactor);
  final image = await recorder.endRecording().toImage(size, size);
  final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
  await File(path).writeAsBytes(bytes!.buffer.asUint8List());
  // ignore: avoid_print
  print('gerado $path');
}

void main() {
  test('gera PNGs do logo', () async {
    await _write('assets/logo/cactusguard-icon.png', 1024, 0.72, background: Colors.white);
    // Adaptive icon: o flutter_launcher_icons aplica inset de 16%; 80% aqui ≈ 54% do ícone (zona segura 66%).
    await _write('assets/logo/cactusguard-icon-foreground.png', 1024, 0.80);
    // Fallback PNG do logo (spec 035), fundo transparente.
    await _write('assets/logo/cactusguard-logo.png', 512, 0.92);
  });
}
