package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.UIKit.*
import platform.CoreGraphics.CGRectMake

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayerSurface(playerState: VideoPlayerState, modifier: Modifier) {
    println("[VideoPlayerSurface] Composable called")

    // Remember AVPlayerViewController to prevent recreation on recomposition
    val avPlayerViewController = remember {
        AVPlayerViewController().apply {
            showsPlaybackControls = true
        }
    }

    // Use DisposableEffect to handle cleanup
    DisposableEffect(Unit) {
        onDispose {
            println("[VideoPlayerSurface] Disposing")
            playerState.pause()
            avPlayerViewController.removeFromParentViewController()
        }
    }

    // Effect to update player when it changes
    DisposableEffect(playerState.getPlayer()) {
        println("[VideoPlayerSurface] Player changed to: ${playerState.getPlayer()}")
        avPlayerViewController.player = playerState.getPlayer()
        onDispose { }
    }

    UIKitView(
        modifier = modifier,
        factory = {
            println("[VideoPlayerSurface] UIKitView factory called")

            // Create the container view
            UIView().apply {
                backgroundColor = UIColor.blackColor
                clipsToBounds = true // Ensure content doesn't overflow

                // Add the AVPlayerViewController's view
                avPlayerViewController.view.translatesAutoresizingMaskIntoConstraints = false
                addSubview(avPlayerViewController.view)

                // Get the root view controller and add the AVPlayerViewController
                UIApplication.sharedApplication.keyWindow?.rootViewController?.let { rootVC ->
                    rootVC.addChildViewController(avPlayerViewController)
                    avPlayerViewController.didMoveToParentViewController(rootVC)
                }

                // Set up constraints
                NSLayoutConstraint.activateConstraints(
                    listOf(
                        avPlayerViewController.view.topAnchor.constraintEqualToAnchor(topAnchor),
                        avPlayerViewController.view.leadingAnchor.constraintEqualToAnchor(leadingAnchor),
                        avPlayerViewController.view.trailingAnchor.constraintEqualToAnchor(trailingAnchor),
                        avPlayerViewController.view.bottomAnchor.constraintEqualToAnchor(bottomAnchor)
                    )
                )

                println("[VideoPlayerSurface] View setup completed")
            }
        },
        update = { containerView ->
            println("[VideoPlayerSurface] UIKitView update called")

            // Force layout update
            containerView.setNeedsLayout()
            containerView.layoutIfNeeded()

            // Ensure the AVPlayerViewController view fills the container
            avPlayerViewController.view.setFrame(containerView.bounds)

            // Start playback if we have a player and should be playing
            val player = playerState.getPlayer()
            if (player != null && playerState.hasMedia && !playerState.isPlaying) {
                println("[VideoPlayerSurface] Starting playback")
                playerState.play()
            }
        }
    )
}