import UIKit
import AVFoundation
import MLKitPoseDetection
import MLKitVision
import TrainingApp

final class PoseCameraController: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    private let bridge: IosAppBridge
    private let session = AVCaptureSession()
    private let captureQueue = DispatchQueue(label: "com.mlpt.camera", qos: .userInitiated)
    private let cameraView = UIView()
    private let skeleton = CAShapeLayer()
    private var preview: AVCaptureVideoPreviewLayer!
    private let exercise = UISegmentedControl(items: ["Squat", "Push-up", "Curl"])
    private let camera = UISegmentedControl(items: ["Front", "Rear"])
    private let instructions = UILabel()
    private let feedback = UILabel()
    private let counts = UILabel()
    private let confirm = UISwitch()
    private let start = UIButton(type: .system)
    private let stop = UIButton(type: .system)
    private let history = UITextView()
    private var timer: Timer?
    private var generation = 0
    private var requested = false
    private var captureToken = 0 // Accessed only on captureQueue.
    private var lastInferenceTime: Int64 = 0 // Accessed only on captureQueue.
    private var cameraError: String?
    private let detector: PoseDetector = {
        let options = PoseDetectorOptions(); options.detectorMode = .stream
        return PoseDetector.poseDetector(options: options)
    }()

    init(bridge: IosAppBridge) { self.bridge = bridge; super.init(nibName: nil, bundle: nil) }
    required init?(coder: NSCoder) { fatalError("Use init(bridge:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        overrideUserInterfaceStyle = .light
        view.tintColor = UIColor(red: 21/255, green: 86/255, blue: 216/255, alpha: 1)
        view.backgroundColor = .clear
        let scroll = UIScrollView(); scroll.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scroll)
        NSLayoutConstraint.activate([scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor), scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor), scroll.topAnchor.constraint(equalTo: view.topAnchor), scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor)])
        let stack = UIStackView(); stack.axis = .vertical; stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor), stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor), stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor), stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor), stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor)])
        exercise.selectedSegmentIndex = 0; camera.selectedSegmentIndex = 0
        exercise.accessibilityLabel = "Exercise"; camera.accessibilityLabel = "Camera"; confirm.accessibilityLabel = "Confirm steady side-on camera setup"
        exercise.addTarget(self, action: #selector(selectionChanged), for: .valueChanged)
        camera.addTarget(self, action: #selector(selectionChanged), for: .valueChanged)
        instructions.numberOfLines = 0; feedback.numberOfLines = 0; counts.numberOfLines = 0
        [instructions, feedback, counts].forEach { $0.adjustsFontForContentSizeCategory = true; $0.setContentCompressionResistancePriority(.required, for: .vertical) }
        feedback.font = .preferredFont(forTextStyle: .body)
        cameraView.backgroundColor = .black; cameraView.heightAnchor.constraint(equalToConstant: 280).isActive = true
        cameraView.layer.masksToBounds = true
        preview = AVCaptureVideoPreviewLayer(session: session); preview.videoGravity = .resizeAspect
        cameraView.layer.addSublayer(preview)
        skeleton.fillColor = UIColor.clear.cgColor; skeleton.strokeColor = UIColor.cyan.cgColor; skeleton.lineWidth = 3
        cameraView.layer.addSublayer(skeleton)
        let confirmation = UIStackView(arrangedSubviews: [label("I have a steady, side-on camera view"), confirm]); confirmation.spacing = 12
        confirm.addTarget(self, action: #selector(refresh), for: .valueChanged)
        start.setTitle("Start exercise & log", for: .normal); start.addTarget(self, action: #selector(startTapped), for: .touchUpInside)
        stop.setTitle("Stop & save log", for: .normal); stop.addTarget(self, action: #selector(stopTapped), for: .touchUpInside)
        let retry = UIButton(type: .system); retry.setTitle("Retry loading / save", for: .normal); retry.addTarget(self, action: #selector(retryTapped), for: .touchUpInside)
        let more = UIButton(type: .system); more.setTitle("Load older sessions", for: .normal); more.addTarget(self, action: #selector(moreTapped), for: .touchUpInside)
        let details = UIButton(type: .system); details.setTitle("View session measurements", for: .normal); details.addTarget(self, action: #selector(detailsTapped), for: .touchUpInside)
        history.isEditable = false; history.font = .preferredFont(forTextStyle: .caption1); history.backgroundColor = .secondarySystemBackground
        history.heightAnchor.constraint(equalToConstant: 170).isActive = true
        [exercise, instructions, camera, cameraView, confirmation, start, stop, counts, feedback, retry,
         label("Saved pose sessions"), history, more, details,
         label("Images and full landmark sequences stay off disk. Green means tracked checks matched; it does not certify exercise safety.")].forEach { stack.addArrangedSubview($0) }
        NotificationCenter.default.addObserver(self, selector: #selector(backgrounded), name: UIApplication.didEnterBackgroundNotification, object: nil)
        selectionChanged()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in self?.refresh() }
    }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); end(interrupted: true); timer?.invalidate(); timer = nil }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); preview.frame = cameraView.bounds; skeleton.frame = cameraView.bounds }
    private func label(_ text: String) -> UILabel { let l = UILabel(); l.text = text; l.numberOfLines = 0; l.font = .preferredFont(forTextStyle: .caption1); return l }
    @objc private func selectionChanged() { confirm.isOn = false; instructions.text = bridge.instructions(exercise: Int32(exercise.selectedSegmentIndex)); refresh() }
    @objc private func retryTapped() { bridge.retry() }
    @objc private func moreTapped() { bridge.moreHistory() }
    @objc private func detailsTapped() {
        let chooser = UIAlertController(title: "Choose a saved session", message: nil, preferredStyle: .actionSheet)
        for item in bridge.historyItems() {
            chooser.addAction(UIAlertAction(title: item.title, style: .default) { [weak self] _ in self?.bridge.showDetails(id: item.id) })
        }
        chooser.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        chooser.popoverPresentationController?.sourceView = view
        chooser.popoverPresentationController?.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1)
        present(chooser, animated: true)
    }
    @objc private func backgrounded() { end(interrupted: true) }
    @objc private func stopTapped() { end(interrupted: false) }
    private func end(interrupted: Bool) {
        generation += 1; requested = false
        bridge.stop(interrupted: interrupted); skeleton.path = nil
        captureQueue.async { [self] in if session.isRunning { session.stopRunning() } }
        refresh()
    }
    @objc private func startTapped() {
        guard confirm.isOn, bridge.status().canStart, !requested else { return }
        requested = true; cameraError = nil; generation += 1; let token = generation
        refresh()
        AVCaptureDevice.requestAccess(for: .video) { [weak self] allowed in DispatchQueue.main.async {
            guard let self, self.requested, self.generation == token, self.view.window != nil else { return }
            guard allowed else { self.requested = false; self.cameraError = "Camera access is disabled. Enable it in Settings → ML PT → Camera."; self.refresh(); return }
            guard self.bridge.start(exercise: Int32(self.exercise.selectedSegmentIndex)) else { self.requested = false; self.refresh(); return }
            let front = self.camera.selectedSegmentIndex == 0
            self.captureQueue.async { self.configure(front: front, token: token) }
        } }
    }
    private func configure(front: Bool, token: Int) {
        captureToken = token
        do {
            session.beginConfiguration()
            if session.canSetSessionPreset(.hd1280x720) { session.sessionPreset = .hd1280x720 } else { session.sessionPreset = .vga640x480 }
            session.inputs.forEach { session.removeInput($0) }; session.outputs.forEach { session.removeOutput($0) }
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: front ? .front : .back) else { throw cameraFailure("No camera available. Use a physical iPhone.") }
            let input = try AVCaptureDeviceInput(device: device)
            guard session.canAddInput(input) else { throw cameraFailure("Cannot open camera.") }; session.addInput(input)
            let output = AVCaptureVideoDataOutput(); output.alwaysDiscardsLateVideoFrames = true
            output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
            output.setSampleBufferDelegate(self, queue: captureQueue)
            guard session.canAddOutput(output) else { throw cameraFailure("Camera output unavailable.") }; session.addOutput(output)
            if let connection = output.connection(with: .video) {
                connection.videoOrientation = .portrait
                if connection.isVideoMirroringSupported { connection.automaticallyAdjustsVideoMirroring = false; connection.isVideoMirrored = front }
            }
            session.commitConfiguration()
            DispatchQueue.main.async { [weak self] in
                guard let self, self.generation == token, self.requested else { return }
                self.preview.connection?.videoOrientation = .portrait
                if let c = self.preview.connection, c.isVideoMirroringSupported { c.automaticallyAdjustsVideoMirroring = false; c.isVideoMirrored = front }
            }
            // A cancelled configuration is followed by end()'s queued stopRunning.
            session.startRunning()
        } catch {
            session.commitConfiguration()
            DispatchQueue.main.async { [weak self] in guard let self, self.generation == token else { return }; self.end(interrupted: true); self.cameraError = error.localizedDescription; self.refresh() }
        }
    }
    private func cameraFailure(_ text: String) -> NSError { NSError(domain: "MLPT.Camera", code: 1, userInfo: [NSLocalizedDescriptionKey: text]) }
    @objc private func refresh() {
        let s = bridge.status()
        start.isEnabled = s.canStart && confirm.isOn && !requested
        stop.isEnabled = s.active || requested
        exercise.isEnabled = !s.active && !requested; camera.isEnabled = exercise.isEnabled; confirm.isEnabled = exercise.isEnabled
        counts.text = "\(s.fullReps) full reps · \(s.matchedReps) matched checks · \(s.partialAttempts) partial"
        feedback.text = cameraError ?? s.error ?? (s.saving ? "Saving log…" : s.message)
        history.text = s.history
        if s.quality == "NotVisible" { skeleton.path = nil }
        skeleton.strokeColor = (s.quality == "WithinChecks" ? UIColor.systemGreen : s.quality == "Adjust" ? UIColor.systemOrange : UIColor.systemGray).cgColor
    }
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let token = captureToken
        let time = Int64(ProcessInfo.processInfo.systemUptime * 1000)
        guard time - lastInferenceTime >= 80 else { return }; lastInferenceTime = time
        let width = Float(CVPixelBufferGetWidth(buffer)), height = Float(CVPixelBufferGetHeight(buffer))
        let image = VisionImage(buffer: sampleBuffer); image.orientation = .up // Output buffers already rotated to portrait and mirrored with preview.
        let types: [(String, PoseLandmarkType)] = [("LeftShoulder", .leftShoulder), ("RightShoulder", .rightShoulder), ("LeftElbow", .leftElbow), ("RightElbow", .rightElbow), ("LeftWrist", .leftWrist), ("RightWrist", .rightWrist), ("LeftHip", .leftHip), ("RightHip", .rightHip), ("LeftKnee", .leftKnee), ("RightKnee", .rightKnee), ("LeftAnkle", .leftAnkle), ("RightAnkle", .rightAnkle)]
        do {
            let pose = try detector.results(in: image).first
            let points = types.compactMap { name, type -> IosLandmark? in
                guard let p = pose?.landmark(ofType: type) else { return nil }
                return IosLandmark(name: name, x: Float(p.position.x), y: Float(p.position.y), confidence: p.inFrameLikelihood)
            }
            DispatchQueue.main.async { [weak self] in
                guard let self, self.generation == token, self.requested, self.bridge.status().active else { return }
                self.bridge.frame(timeMs: time, width: width, height: height, landmarks: points)
                self.draw(points, width: width, height: height); self.refresh()
            }
        } catch {
            DispatchQueue.main.async { [weak self] in guard let self, self.generation == token else { return }; self.bridge.resetTracking(); self.skeleton.path = nil }
        }
    }
    private func draw(_ points: [IosLandmark], width: Float, height: Float) {
        let scale = min(cameraView.bounds.width / CGFloat(width), cameraView.bounds.height / CGFloat(height))
        let origin = CGPoint(x: (cameraView.bounds.width - CGFloat(width) * scale) / 2, y: (cameraView.bounds.height - CGFloat(height) * scale) / 2)
        let path = UIBezierPath()
        let edges = [(0,1), (0,2), (2,4), (1,3), (3,5), (0,6), (1,7), (6,7), (6,8), (8,10), (7,9), (9,11)]
        guard points.count == 12 else { skeleton.path = nil; return }
        for (a,b) in edges where points[a].confidence >= 0.5 && points[b].confidence >= 0.5 {
            path.move(to: CGPoint(x: origin.x + CGFloat(points[a].x) * scale, y: origin.y + CGFloat(points[a].y) * scale))
            path.addLine(to: CGPoint(x: origin.x + CGFloat(points[b].x) * scale, y: origin.y + CGFloat(points[b].y) * scale))
        }
        skeleton.path = path.cgPath
    }
    deinit { timer?.invalidate(); NotificationCenter.default.removeObserver(self) }
}
