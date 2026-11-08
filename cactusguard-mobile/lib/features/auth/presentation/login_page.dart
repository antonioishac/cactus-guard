import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/api_error.dart';
import '../../../core/api/mock/fake_backend.dart';
import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/widgets/logo.dart';

/// Login (spec 022): usuário + senha, validação no cliente, mensagens claras.
/// Em sucesso o redirect do router leva ao destino (`?from=`) ou à home.
class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  final _form = GlobalKey<FormState>();
  final _user = TextEditingController();
  final _pass = TextEditingController();
  bool _loading = false;
  bool _obscure = true;
  String? _error;
  String? _notice;

  @override
  void initState() {
    super.initState();
    final store = ref.read(sessionStoreProvider);
    _notice = store.notice?.message;
    store.consumeNotice();
  }

  @override
  void dispose() {
    _user.dispose();
    _pass.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!(_form.currentState?.validate() ?? false)) return;
    setState(() {
      _loading = true;
      _error = null;
      _notice = null;
    });
    try {
      await ref.read(authControllerProvider).login(_user.text, _pass.text);
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.userMessage);
    } finally {
      if (mounted) setState(() => _loading = false);
      _pass.clear();
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final mock = ref.watch(appConfigProvider).useMock;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: AutofillGroup(
                child: Form(
                  key: _form,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Center(child: CgLogo(size: 40)),
                      const SizedBox(height: 8),
                      Text('Prevenção à lavagem de dinheiro',
                          textAlign: TextAlign.center, style: t.textTheme.bodyMedium?.copyWith(color: AppColors.muted)),
                      const SizedBox(height: 32),
                      if (_notice != null) _Banner(text: _notice!, color: AppColors.warning, bg: AppColors.warningBg),
                      if (_error != null) _Banner(key: const Key('login-error'), text: _error!, color: AppColors.danger, bg: AppColors.dangerBg),
                      TextFormField(
                        key: const Key('login-username'),
                        controller: _user,
                        enabled: !_loading,
                        autofillHints: const [AutofillHints.username],
                        textInputAction: TextInputAction.next,
                        autocorrect: false,
                        enableSuggestions: false,
                        decoration: const InputDecoration(labelText: 'Usuário', prefixIcon: Icon(Icons.person_outline)),
                        validator: (v) => (v == null || v.trim().isEmpty) ? 'Informe o usuário' : null,
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        key: const Key('login-password'),
                        controller: _pass,
                        enabled: !_loading,
                        obscureText: _obscure,
                        autofillHints: const [AutofillHints.password],
                        textInputAction: TextInputAction.done,
                        onFieldSubmitted: (_) => _submit(),
                        decoration: InputDecoration(
                          labelText: 'Senha',
                          prefixIcon: const Icon(Icons.lock_outline),
                          suffixIcon: IconButton(
                            tooltip: _obscure ? 'Mostrar senha' : 'Ocultar senha',
                            icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                            onPressed: () => setState(() => _obscure = !_obscure),
                          ),
                        ),
                        validator: (v) => (v == null || v.isEmpty) ? 'Informe a senha' : null,
                      ),
                      const SizedBox(height: 24),
                      FilledButton(
                        key: const Key('login-submit'),
                        onPressed: _loading ? null : _submit,
                        child: _loading
                            ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
                            : const Text('Entrar'),
                      ),
                      if (mock) ...[
                        const SizedBox(height: 24),
                        Text(
                          'Modo demonstração (dados fictícios). Usuários: analista, supervisor, admin, auditor — senha ${FakeBackend.password}',
                          textAlign: TextAlign.center,
                          style: t.textTheme.bodySmall?.copyWith(color: AppColors.muted),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Banner extends StatelessWidget {
  const _Banner({super.key, required this.text, required this.color, required this.bg});
  final String text;
  final Color color;
  final Color bg;

  @override
  Widget build(BuildContext context) => Container(
        margin: const EdgeInsets.only(bottom: 16),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(10), border: Border.all(color: color.withValues(alpha: 0.3))),
        child: Text(text, style: TextStyle(color: color)),
      );
}
