import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../theme/app_colors.dart';

/// Símbolo oficial (spec 035). Variante escura troca o charcoal por cinza claro para contraste.
class CgLogoMark extends StatelessWidget {
  const CgLogoMark({super.key, this.size = 32, this.onDark});

  final double size;

  /// Força a variante para fundo escuro; `null` segue o tema.
  final bool? onDark;

  @override
  Widget build(BuildContext context) {
    final dark = onDark ?? Theme.of(context).brightness == Brightness.dark;
    return SvgPicture.asset(
      dark ? 'assets/logo/cactusguard-logo-dark.svg' : 'assets/logo/cactusguard-logo.svg',
      width: size,
      height: size,
      semanticsLabel: 'CactusGuard',
    );
  }
}

/// Logo horizontal: símbolo + "Cactus" (charcoal) + "Guard" (dourado).
class CgLogo extends StatelessWidget {
  const CgLogo({super.key, this.size = 28, this.onDark});

  final double size;
  final bool? onDark;

  @override
  Widget build(BuildContext context) {
    final dark = onDark ?? Theme.of(context).brightness == Brightness.dark;
    final style = TextStyle(fontSize: size * 0.72, fontWeight: FontWeight.w700, letterSpacing: -0.3);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        CgLogoMark(size: size, onDark: dark),
        SizedBox(width: size * 0.3),
        Text.rich(
          TextSpan(children: [
            TextSpan(text: 'Cactus', style: style.copyWith(color: dark ? Colors.white : AppColors.neutral)),
            TextSpan(text: 'Guard', style: style.copyWith(color: AppColors.gold)),
          ]),
          semanticsLabel: 'CactusGuard',
        ),
      ],
    );
  }
}
