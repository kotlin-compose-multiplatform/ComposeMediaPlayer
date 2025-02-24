@file:OptIn(ExperimentalForeignApi::class)

package io.github.kdroidfilter.composemediaplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVKit.AVPlayerViewController
import platform.UIKit.*

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayerSurface(playerState: VideoPlayerState, modifier: Modifier) {
    // Création et mémorisation du AVPlayerViewController
    val avPlayerViewController = remember {
        AVPlayerViewController().apply {
            showsPlaybackControls = false
        }
    }

    // Nettoyage lors de la suppression de la vue
    DisposableEffect(Unit) {
        onDispose {
            println("[VideoPlayerSurface] Disposing")
            playerState.pause()
            avPlayerViewController.removeFromParentViewController()
        }
    }

    // Mise à jour du player lorsqu'il change
    DisposableEffect(playerState.player) {
        println("[VideoPlayerSurface] Player mis à jour")
        avPlayerViewController.player = playerState.player
        onDispose { }
    }
    if (playerState.hasMedia) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {

            UIKitView(
            modifier = Modifier.fillMaxHeight().aspectRatio(playerState.videoAspectRatio.toFloat()),
                factory = {
                    UIView().apply {
                        backgroundColor = UIColor.blackColor
                        clipsToBounds = true

                        avPlayerViewController.view.translatesAutoresizingMaskIntoConstraints = false
                        addSubview(avPlayerViewController.view)

                        UIApplication.sharedApplication.keyWindow?.rootViewController?.let { rootVC ->
                            rootVC.addChildViewController(avPlayerViewController)
                            avPlayerViewController.didMoveToParentViewController(rootVC)
                        }

                        NSLayoutConstraint.activateConstraints(
                            listOf(
                                avPlayerViewController.view.topAnchor.constraintEqualToAnchor(this.topAnchor),
                                avPlayerViewController.view.leadingAnchor.constraintEqualToAnchor(this.leadingAnchor),
                                avPlayerViewController.view.trailingAnchor.constraintEqualToAnchor(this.trailingAnchor),
                                avPlayerViewController.view.bottomAnchor.constraintEqualToAnchor(this.bottomAnchor)
                            )
                        )
                        println("[VideoPlayerSurface] Vue configurée")
                    }
                },
                update = { containerView ->
                    // Masquer ou afficher la vue en fonction de la présence d'un média
                    containerView.hidden = !playerState.hasMedia

                    containerView.setNeedsLayout()
                    containerView.layoutIfNeeded()
                    avPlayerViewController.view.setFrame(containerView.bounds)

                    // Démarrer la lecture si le média est chargé et non déjà en lecture
                    if (playerState.player != null && playerState.hasMedia && !playerState.isPlaying) {
                        println("[VideoPlayerSurface] Démarrage de la lecture")
                        playerState.play()
                    }
                }
            )
        }
    }
}
