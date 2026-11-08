import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../utils/mask.dart';

/// Pílula de severidade/status. As cores seguem o web (danger/orange/warning/success/info/neutral).
class StatusBadge extends StatelessWidget {
  const StatusBadge({super.key, required this.label, required this.foreground, required this.background, this.icon});

  /// Severidade: LOW, MEDIUM, HIGH, CRITICAL.
  factory StatusBadge.severity(String severity, {Key? key}) {
    final (fg, bg) = switch (severity) {
      'CRITICAL' => (AppColors.danger, AppColors.dangerBg),
      'HIGH' => (AppColors.orange, AppColors.orangeBg),
      'MEDIUM' => (AppColors.warning, AppColors.warningBg),
      'LOW' => (AppColors.success, AppColors.successBg),
      _ => (AppColors.neutral, AppColors.neutralBg),
    };
    return StatusBadge(key: key, label: Labels.severity(severity), foreground: fg, background: bg);
  }

  /// Status de alerta/case.
  factory StatusBadge.status(String status, {Key? key}) {
    final (fg, bg) = switch (status) {
      'OPEN' => (AppColors.info, AppColors.infoBg),
      'IN_ANALYSIS' => (AppColors.brand, AppColors.successBg),
      'ESCALATED' || 'PENDING_APPROVAL' => (AppColors.orange, AppColors.orangeBg),
      'COMMUNICATE' => (AppColors.danger, AppColors.dangerBg),
      'FALSE_POSITIVE' || 'ARCHIVED' || 'CLOSED' => (AppColors.neutral, AppColors.neutralBg),
      _ => (AppColors.neutral, AppColors.neutralBg),
    };
    return StatusBadge(key: key, label: Labels.status(status), foreground: fg, background: bg);
  }

  final String label;
  final Color foreground;
  final Color background;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: foreground.withValues(alpha: 0.25)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[Icon(icon, size: 12, color: foreground), const SizedBox(width: 4)],
          Text(label, style: TextStyle(color: foreground, fontSize: 12, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}
