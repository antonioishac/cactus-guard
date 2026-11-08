import 'package:flutter/material.dart';

import 'logo.dart';

/// Scaffold padrão: header com logo (spec 035) e título opcional da seção.
class AppScaffold extends StatelessWidget {
  const AppScaffold({
    super.key,
    required this.body,
    this.title,
    this.actions,
    this.showLogo = true,
    this.bottomNavigationBar,
    this.floatingActionButton,
  });

  final Widget body;
  final String? title;
  final List<Widget>? actions;
  final bool showLogo;
  final Widget? bottomNavigationBar;
  final Widget? floatingActionButton;

  @override
  Widget build(BuildContext context) {
    final canPop = Navigator.of(context).canPop();
    return Scaffold(
      appBar: AppBar(
        titleSpacing: canPop ? 0 : 16,
        title: Row(
          children: [
            if (showLogo) const CgLogoMark(size: 26),
            if (showLogo && title != null) const SizedBox(width: 10),
            if (title != null)
              Flexible(child: Text(title!, overflow: TextOverflow.ellipsis))
            else if (showLogo)
              const Padding(padding: EdgeInsets.only(left: 8), child: Text('CactusGuard')),
          ],
        ),
        actions: actions,
      ),
      body: SafeArea(child: body),
      bottomNavigationBar: bottomNavigationBar,
      floatingActionButton: floatingActionButton,
    );
  }
}
