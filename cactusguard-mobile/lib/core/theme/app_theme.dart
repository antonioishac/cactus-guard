import 'package:flutter/material.dart';

import 'app_colors.dart';

/// Tema Material 3 claro/escuro com a paleta CactusGuard.
abstract final class AppTheme {
  static ThemeData light() {
    final scheme = ColorScheme.fromSeed(
      seedColor: AppColors.accent,
      brightness: Brightness.light,
    ).copyWith(
      primary: AppColors.brand,
      onPrimary: Colors.white,
      secondary: AppColors.gold,
      onSecondary: AppColors.ink,
      tertiary: AppColors.sidebar,
      error: AppColors.danger,
      surface: Colors.white,
      onSurface: AppColors.ink,
      outlineVariant: AppColors.line,
    );
    return _base(scheme).copyWith(scaffoldBackgroundColor: AppColors.canvas);
  }

  static ThemeData dark() {
    final scheme = ColorScheme.fromSeed(
      seedColor: AppColors.accent,
      brightness: Brightness.dark,
    ).copyWith(
      primary: AppColors.accent,
      onPrimary: AppColors.sidebar,
      secondary: AppColors.gold,
      onSecondary: AppColors.ink,
      tertiary: AppColors.accent,
      surface: const Color(0xFF0F1F1C),
      onSurface: const Color(0xFFE6ECEB),
    );
    return _base(scheme).copyWith(scaffoldBackgroundColor: const Color(0xFF0A1714));
  }

  static ThemeData _base(ColorScheme scheme) {
    final isDark = scheme.brightness == Brightness.dark;
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      appBarTheme: AppBarTheme(
        backgroundColor: isDark ? AppColors.sidebar : Colors.white,
        foregroundColor: isDark ? Colors.white : AppColors.ink,
        elevation: 0,
        scrolledUnderElevation: 1,
        centerTitle: false,
      ),
      navigationBarTheme: NavigationBarThemeData(
        indicatorColor: AppColors.accent.withValues(alpha: isDark ? 0.30 : 0.22),
        labelTextStyle: WidgetStatePropertyAll(
          TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: scheme.onSurface),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
        filled: true,
        fillColor: isDark ? const Color(0xFF13302B) : Colors.white,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(48),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        ),
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: BorderSide(color: isDark ? const Color(0xFF1E3B36) : AppColors.line),
        ),
      ),
      snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
    );
  }
}
