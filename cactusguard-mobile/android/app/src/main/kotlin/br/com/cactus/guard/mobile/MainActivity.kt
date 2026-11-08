package br.com.cactus.guard.mobile

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import io.flutter.embedding.android.FlutterFragmentActivity

/**
 * FlutterFragmentActivity: exigido pelo local_auth (BiometricPrompt).
 *
 * Segurança (spec 024): FLAG_SECURE bloqueia screenshot, gravação de tela e o preview
 * no app switcher (Recentes). Aplicado antes de qualquer frame do Flutter.
 */
class MainActivity : FlutterFragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Reforço: nenhuma captura do conteúdo em Recentes (Android 13+).
            setRecentsScreenshotEnabled(false)
        }
        super.onCreate(savedInstanceState)
    }
}
