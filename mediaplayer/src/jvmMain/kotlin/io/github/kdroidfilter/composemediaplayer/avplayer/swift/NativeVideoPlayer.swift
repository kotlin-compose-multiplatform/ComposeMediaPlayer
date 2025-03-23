//
//  SharedVideoPlayer.swift
//
//  Créé par Elie Gambache le 23/03/2025
//

import Foundation
import AVFoundation
import CoreVideo
import CoreGraphics
import Accelerate

/// Classe qui gère la lecture vidéo et la capture des frames dans un buffer partagé.
class SharedVideoPlayer {
    var player: AVPlayer?
    var videoOutput: AVPlayerItemVideoOutput?
    
    // On remplace l'ancien Timer 30fps par un intervalle ~60fps
    var timer: Timer?
    
    // latestFrameData contiendra les pixels au format ARGB (0xAARRGGBB)
    var latestFrameData: [UInt32] = []
    
    // Dimensions de la frame (fixées ici)
    let frameWidth: Int = 640
    let frameHeight: Int = 360

    init() {
        // Utilisation du format BGRA pour des performances optimisées
        let pixelBufferAttributes: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: frameWidth,
            kCVPixelBufferHeightKey as String: frameHeight
        ]
        videoOutput = AVPlayerItemVideoOutput(pixelBufferAttributes: pixelBufferAttributes)
        
        // Pré-allocation du buffer pour stocker les pixels en ARGB
        latestFrameData = Array(repeating: 0, count: frameWidth * frameHeight)
    }
    
    func openUri(_ uri: String) {
        let url: URL
        if let parsedURL = URL(string: uri), parsedURL.scheme != nil {
            // URL réseau ou locale
            url = parsedURL
        } else {
            // Chemin de fichier local
            url = URL(fileURLWithPath: uri)
        }
        
        let asset = AVAsset(url: url)
        let item = AVPlayerItem(asset: asset)
        
        if let output = videoOutput {
            item.add(output)
        }
        player = AVPlayer(playerItem: item)
        
        // Démarrage d'un Timer ~60fps (0.0167 s)
        DispatchQueue.main.async {
            self.timer?.invalidate()
            self.timer = Timer.scheduledTimer(withTimeInterval: 0.0167, repeats: true) { _ in
                self.captureFrame()
            }
        }
    }
    
    func captureFrame() {
        guard let output = videoOutput,
              let item = player?.currentItem else { return }
        
        let currentTime = item.currentTime()
        if output.hasNewPixelBuffer(forItemTime: currentTime),
           let pixelBuffer = output.copyPixelBuffer(forItemTime: currentTime, itemTimeForDisplay: nil) {
            updateLatestFrameData(from: pixelBuffer)
        }
    }
    
    /// Conversion du format BGRA en ARGB pour compatibilité avec BufferedImage en Java
    func updateLatestFrameData(from pixelBuffer: CVPixelBuffer) {
        CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }

        guard let baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer) else { return }

        let width = CVPixelBufferGetWidth(pixelBuffer)
        let height = CVPixelBufferGetHeight(pixelBuffer)
        let srcBytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)

        guard width == frameWidth, height == frameHeight else {
            print("Dimensions de la frame inattendues : \(width)x\(height)")
            return
        }

        latestFrameData.withUnsafeMutableBytes { destRawBuffer in
            // Si le stride correspond à la largeur * 4 (octets par pixel), on copie directement
            if srcBytesPerRow == width * 4 {
                memcpy(destRawBuffer.baseAddress, baseAddress, height * srcBytesPerRow)
            } else {
                // Sinon, copie ligne par ligne
                for row in 0..<height {
                    let srcRow = baseAddress.advanced(by: row * srcBytesPerRow)
                    let destRow = destRawBuffer.baseAddress!.advanced(by: row * width * 4)
                    memcpy(destRow, srcRow, width * 4)
                }
            }
        }
    }

    
    func play() {
        player?.play()
    }
    
    func pause() {
        player?.pause()
    }
    
    /// Retourne un pointeur alloué contenant la dernière frame (pixels en ARGB)
    /// L'appelant devra libérer ce pointeur après usage, le code Java se charge juste de lire et de copier.
    func getLatestFramePointer() -> UnsafeMutablePointer<UInt32>? {
        if latestFrameData.isEmpty { return nil }
        let count = latestFrameData.count
        let pointer = UnsafeMutablePointer<UInt32>.allocate(capacity: count)
        pointer.initialize(from: latestFrameData, count: count)
        return pointer
    }
    
    func getFrameWidth() -> Int { frameWidth }
    func getFrameHeight() -> Int { frameHeight }
    
    func dispose() {
        player?.pause()
        player = nil
        timer?.invalidate()
        timer = nil
        latestFrameData.removeAll()
    }
}

/// MARK: - Fonctions C exportées pour JNA
@_cdecl("createVideoPlayer")
public func createVideoPlayer() -> UnsafeMutableRawPointer? {
    let player = SharedVideoPlayer()
    return Unmanaged.passRetained(player).toOpaque()
}

@_cdecl("openUri")
public func openUri(_ context: UnsafeMutableRawPointer?, _ uri: UnsafePointer<CChar>?) {
    guard let context = context,
          let uriCStr = uri,
          let swiftUri = String(validatingUTF8: uriCStr)
    else {
        print("Paramètres invalides pour openUri")
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

@_cdecl("disposeVideoPlayer")
public func disposeVideoPlayer(_ context: UnsafeMutableRawPointer?) {
    guard let context = context else { return }
    let player = Unmanaged<SharedVideoPlayer>.fromOpaque(context).takeRetainedValue()
    DispatchQueue.main.async {
        player.dispose()
    }
}
