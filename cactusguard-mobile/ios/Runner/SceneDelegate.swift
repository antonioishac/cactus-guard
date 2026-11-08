import Flutter
import UIKit

/// Segurança (spec 024): ao sair do primeiro plano, cobre a janela com uma tela neutra
/// (cor da marca) para que o snapshot do app switcher não exiba dados de alertas/cases.
class SceneDelegate: FlutterSceneDelegate {
  private var privacyView: UIView?

  override func sceneWillResignActive(_ scene: UIScene) {
    super.sceneWillResignActive(scene)
    guard privacyView == nil, let window = (scene as? UIWindowScene)?.windows.first else { return }
    let cover = UIView(frame: window.bounds)
    cover.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    cover.backgroundColor = UIColor(red: 0x0d / 255.0, green: 0x3d / 255.0, blue: 0x36 / 255.0, alpha: 1)
    window.addSubview(cover)
    privacyView = cover
  }

  override func sceneDidBecomeActive(_ scene: UIScene) {
    super.sceneDidBecomeActive(scene)
    privacyView?.removeFromSuperview()
    privacyView = nil
  }
}
