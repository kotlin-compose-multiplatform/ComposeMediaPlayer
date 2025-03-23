import Foundation
import AVFoundation
import CoreVideo
import CoreGraphics
#if canImport(UIKit)
import UIKit
#endif

/// Class that manages video playback and frame capture into an optimized shared buffer.
class SharedVideoPlayer {
    private var player: AVPlayer?
    private var videoOutput: AVPlayerItemVideoOutput?

    // CADisplayLink for capturing frames at screen refresh rate (~60fps)
    private var displayLink: CADisplayLink?

    // Shared buffer to store the frame in BGRA format (no conversion needed)
    private var frameBuffer: UnsafeMutablePointer<UInt32>?
    private var bufferCapacity: Int = 0

    // Frame dimensions
    private var frameWidth: Int = 0
    private var frameHeight: Int = 0

    // Audio volume control (0.0 to 1.0)
    private var volume: Float = 1.0

    init() {}

    /// Opens the video from the given URI (local or network)
    func openUri(_ uri: String) {
        // Determine the URL (local or network)
        let url: URL = {
            if let parsedURL = URL(string: uri), parsedURL.scheme != nil {
                return parsedURL
            } else {
                return URL(fileURLWithPath: uri)
            }
        }()

        let asset = AVAsset(url: url)

        // Retrieve the video track to obtain the actual dimensions
        guard let videoTrack = asset.tracks(withMediaType: .video).first else {
            print("Video track not found")
            return
        }

        let naturalSize = videoTrack.naturalSize
        let transform = videoTrack.preferredTransform
        let effectiveSize = naturalSize.applying(transform)
        frameWidth = Int(abs(effectiveSize.width))
        frameHeight = Int(abs(effectiveSize.height))

        // Allocate or reuse the shared buffer if capacity matches
        let totalPixels = frameWidth * frameHeight
        if let buffer = frameBuffer, bufferCapacity == totalPixels {
            buffer.initialize(repeating: 0, count: totalPixels)
        } else {
            frameBuffer?.deallocate()
            frameBuffer = UnsafeMutablePointer<UInt32>.allocate(capacity: totalPixels)
            frameBuffer?.initialize(repeating: 0, count: totalPixels)
            bufferCapacity = totalPixels
        }

        // Create attributes for the CVPixelBuffer (BGRA format) with IOSurface for better performance
        let pixelBufferAttributes: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: frameWidth,
            kCVPixelBufferHeightKey as String: frameHeight,
            kCVPixelBufferIOSurfacePropertiesKey as String: [:]
        ]
        videoOutput = AVPlayerItemVideoOutput(pixelBufferAttributes: pixelBufferAttributes)

        let item = AVPlayerItem(asset: asset)
        if let output = videoOutput {
            item.add(output)
        }
        player = AVPlayer(playerItem: item)

        // Set initial volume
        player?.volume = volume

        // Start display link for frame capture
        startDisplayLink()
    }

    /// Starts the CADisplayLink for capturing frames at screen refresh rate (~60fps)
    private func startDisplayLink() {
        stopDisplayLink() // Ensure previous link is invalidated
        #if canImport(UIKit)
        displayLink = CADisplayLink(target: self, selector: #selector(captureFrame))
        displayLink?.add(to: .main, forMode: .common)
        #elseif os(macOS)
        // For macOS, use CVDisplayLink or fallback to Timer if needed.
        Timer.scheduledTimer(withTimeInterval: 0.0167, repeats: true) { [weak self] _ in
            self?.captureFrame()
        }
        #endif
    }

    /// Stops the CADisplayLink
    private func stopDisplayLink() {
        displayLink?.invalidate()
        displayLink = nil
    }

    /// Captures the latest frame from the video output if available.
    @objc private func captureFrame() {
        guard let output = videoOutput,
              let item = player?.currentItem,
              player?.rate != 0 else { return } // Skip capture if video is not playing

        let currentTime = item.currentTime()
        if output.hasNewPixelBuffer(forItemTime: currentTime),
           let pixelBuffer = output.copyPixelBuffer(forItemTime: currentTime, itemTimeForDisplay: nil) {
            updateLatestFrameData(from: pixelBuffer)
        }
    }

    /// Directly copies the content of the pixelBuffer into the shared buffer without conversion.
    private func updateLatestFrameData(from pixelBuffer: CVPixelBuffer) {
        guard let destBuffer = frameBuffer else { return }

        CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }

        guard let srcBaseAddress = CVPixelBufferGetBaseAddress(pixelBuffer) else { return }
        let width = CVPixelBufferGetWidth(pixelBuffer)
        let height = CVPixelBufferGetHeight(pixelBuffer)
        let srcBytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)

        guard width == frameWidth, height == frameHeight else {
            print("Unexpected dimensions: \(width)x\(height)")
            return
        }

        if srcBytesPerRow == width * 4 {
            memcpy(destBuffer, srcBaseAddress, height * srcBytesPerRow)
        } else {
            for row in 0..<height {
                let srcRow = srcBaseAddress.advanced(by: row * srcBytesPerRow)
                let destRow = destBuffer.advanced(by: row * width)
                memcpy(destRow, srcRow, width * 4)
            }
        }
    }

    /// Starts video playback and resumes frame capture.
    func play() {
        player?.play()
        startDisplayLink()
    }

    /// Pauses video playback and stops frame capture.
    func pause() {
        player?.pause()
        stopDisplayLink()
    }

    /// Sets the volume level (0.0 to 1.0)
    func setVolume(level: Float) {
        volume = max(0.0, min(1.0, level)) // Clamp between 0.0 and 1.0
        player?.volume = volume
    }

    /// Gets the current volume level (0.0 to 1.0)
    func getVolume() -> Float {
        return volume
    }

    /// Returns a pointer to the shared frame buffer. The caller should not free this pointer.
    func getLatestFramePointer() -> UnsafeMutablePointer<UInt32>? {
        return frameBuffer
    }

    func getFrameWidth() -> Int { return frameWidth }
    func getFrameHeight() -> Int { return frameHeight }

    /// Returns the duration of the video in seconds.
    func getDuration() -> Double {
        guard let item = player?.currentItem else { return 0 }
        return CMTimeGetSeconds(item.asset.duration)
    }

    /// Returns the current playback time in seconds.
    func getCurrentTime() -> Double {
        guard let item = player?.currentItem else { return 0 }
        return CMTimeGetSeconds(item.currentTime())
    }

    /// Seeks to the specified time (in seconds).
    func seekTo(time: Double) {
        guard let player = player else { return }
        let newTime = CMTime(seconds: time, preferredTimescale: 600)
        player.seek(to: newTime)
    }

    /// Disposes of the video player and releases resources.
    func dispose() {
        pause()
        player = nil
        videoOutput = nil
        if let buffer = frameBuffer {
            buffer.deallocate()
            frameBuffer = nil
            bufferCapacity = 0
        }
    }
}

/// MARK: - C Exported Functions for JNA

@_cdecl("createVideoPlayer")
public func createVideoPlayer() -> UnsafeMutableRawPointer? {
    let player = SharedVideoPlayer()
    return Unmanaged.passRetained(player).toOpaque()
}

@_cdecl("openUri")
public func openUri(_ context: UnsafeMutableRawPointer?, _ uri: UnsafePointer<CChar>?) {
    guard let context = context,
          let uriCStr = uri,
          let swiftUri = String(validatingUTF8: uriCStr) else {
        print("Invalid parameters for openUri")
        return
    }
    let player = Unmanaged<SharedVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    DispatchQueue.main.async {
        player.openUri(swiftUri)
    }
}

@_cdecl("playVideo")
public func playVideo(_ context: UnsafeMutableRawPointer?) {
    guard let context = context else { return }
    let player = Unmanaged<SharedVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    DispatchQueue.main.async {
        player.play()
    }
}

@_cdecl("pauseVideo")
public func pauseVideo(_ context: UnsafeMutableRawPointer?) {
    guard let context = context else { return }
    let player = Unmanaged<SharedVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    DispatchQueue.main.async {
        player.pause()
    }
}

@_cdecl("setVolume")
public func setVolume(_ context: UnsafeMutableRawPointer?, _ volume: Float) {
    guard let context = context else { return }
    let player = Unmanaged<SharedVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    DispatchQueue.main.async {
        player.setVolume(level: volume)
    }
}

@_cdecl("getVolume")
public func getVolume(_ context: UnsafeMutableRawPointer?) -> Float {
    guard let context = context else { return 0.0 }
    let player = Unmanaged<SharedVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getVolume()
}

@_cdecl("getLatestFrame")
public func getLatestFrame(_ context: UnsafeMutableRawPointer?) -> UnsafeMutableRawPointer? {
    guard let context = context else { return nil }
    let player = Unmanaged<SharedVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    if let ptr = player.getLatestFramePointer() {
        return UnsafeMutableRawPointer(ptr)
    }
    return nil
}

@_cdecl("getFrameWidth")
public func getFrameWidth(_ context: UnsafeMutableRawPointer?) -> Int32 {
    guard let context = context else { return 0 }
    let player = Unmanaged<SharedVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return Int32(player.getFrameWidth())
}

@_cdecl("getFrameHeight")
public func getFrameHeight(_ context: UnsafeMutableRawPointer?) -> Int32 {
    guard let context = context else { return 0 }
    let player = Unmanaged<SharedVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return Int32(player.getFrameHeight())
}

@_cdecl("getVideoDuration")
public func getVideoDuration(_ context: UnsafeMutableRawPointer?) -> Double {
    guard let context = context else { return 0 }
    let player = Unmanaged<SharedVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getDuration()
}

@_cdecl("getCurrentTime")
public func getCurrentTime(_ context: UnsafeMutableRawPointer?) -> Double {
    guard let context = context else { return 0 }
    let player = Unmanaged<SharedVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    return player.getCurrentTime()
}

@_cdecl("seekTo")
public func seekTo(_ context: UnsafeMutableRawPointer?, _ time: Double) {
    guard let context = context else { return }
    let player = Unmanaged<SharedVideoPlayer>.fromOpaque(context).takeUnretainedValue()
    DispatchQueue.main.async {
        player.seekTo(time: time)
    }
}

@_cdecl("disposeVideoPlayer")
public func disposeVideoPlayer(_ context: UnsafeMutableRawPointer?) {
    guard let context = context else { return }
    let player = Unmanaged<SharedVideoPlayer>.fromOpaque(context).takeRetainedValue()
    DispatchQueue.main.async {
        player.dispose()
    }
}