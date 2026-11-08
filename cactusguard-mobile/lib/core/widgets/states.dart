import 'package:flutter/material.dart';

import '../api/api_error.dart';

/// Estado vazio.
class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.title, this.message, this.icon = Icons.inbox_outlined, this.action});

  final String title;
  final String? message;
  final IconData icon;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 56, color: t.colorScheme.outline),
            const SizedBox(height: 16),
            Text(title, style: t.textTheme.titleMedium, textAlign: TextAlign.center),
            if (message != null) ...[
              const SizedBox(height: 8),
              Text(message!, style: t.textTheme.bodyMedium, textAlign: TextAlign.center),
            ],
            if (action != null) ...[const SizedBox(height: 16), action!],
          ],
        ),
      ),
    );
  }
}

/// Estado de erro com "Tentar novamente". Aceita qualquer erro; mostra a mensagem amigável + ref. traceId.
class ErrorState extends StatelessWidget {
  const ErrorState({super.key, required this.error, this.onRetry});

  final Object error;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final e = ApiError.from(error);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(e.isNetwork ? Icons.wifi_off : Icons.error_outline, size: 56, color: t.colorScheme.error),
            const SizedBox(height: 16),
            Text('Não foi possível carregar', style: t.textTheme.titleMedium),
            const SizedBox(height: 8),
            Text(e.userMessage, textAlign: TextAlign.center, style: t.textTheme.bodyMedium),
            if (onRetry != null) ...[
              const SizedBox(height: 16),
              OutlinedButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh), label: const Text('Tentar novamente')),
            ],
          ],
        ),
      ),
    );
  }
}

/// Loading centralizado.
class LoadingState extends StatelessWidget {
  const LoadingState({super.key, this.message});
  final String? message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const CircularProgressIndicator(),
          if (message != null) ...[const SizedBox(height: 12), Text(message!)],
        ],
      ),
    );
  }
}

/// Skeleton de lista (placeholders cinza pulsando) para carregamento de listas.
class SkeletonList extends StatefulWidget {
  const SkeletonList({super.key, this.itemCount = 6});
  final int itemCount;

  @override
  State<SkeletonList> createState() => _SkeletonListState();
}

class _SkeletonListState extends State<SkeletonList> with SingleTickerProviderStateMixin {
  late final AnimationController _c =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 900))..repeat(reverse: true);

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final base = Theme.of(context).colorScheme.surfaceContainerHighest;
    return Semantics(
      label: 'Carregando',
      child: FadeTransition(
        opacity: Tween(begin: 0.45, end: 1.0).animate(_c),
        child: ListView.separated(
          physics: const NeverScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          itemCount: widget.itemCount,
          separatorBuilder: (_, _) => const SizedBox(height: 12),
          itemBuilder: (_, _) => Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _bar(base, 0.7, 14),
              const SizedBox(height: 8),
              _bar(base, 0.45, 12),
            ],
          ),
        ),
      ),
    );
  }

  Widget _bar(Color c, double widthFactor, double h) => FractionallySizedBox(
        widthFactor: widthFactor,
        child: Container(height: h, decoration: BoxDecoration(color: c, borderRadius: BorderRadius.circular(6))),
      );
}
