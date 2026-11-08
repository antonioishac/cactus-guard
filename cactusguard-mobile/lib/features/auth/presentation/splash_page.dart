import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/widgets/logo.dart';

/// Splash Flutter (continua o splash nativo): restaura a sessão; o redirect do router decide o destino.
class SplashPage extends ConsumerStatefulWidget {
  const SplashPage({super.key});

  @override
  ConsumerState<SplashPage> createState() => _SplashPageState();
}

class _SplashPageState extends ConsumerState<SplashPage> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => ref.read(authControllerProvider).restore());
  }

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Scaffold(
      backgroundColor: dark ? AppColors.sidebar : Colors.white,
      body: const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CgLogo(size: 44),
            SizedBox(height: 32),
            SizedBox(width: 28, height: 28, child: CircularProgressIndicator(strokeWidth: 3)),
          ],
        ),
      ),
    );
  }
}
