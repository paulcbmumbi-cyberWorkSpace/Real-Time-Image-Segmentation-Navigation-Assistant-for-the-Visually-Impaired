# Uriel - Real-Time Image Segmentation & Navigation Assistant for the Visually Impaired

**Uriel** is an Android accessibility application designed to empower visually impaired individuals to navigate indoor and outdoor environments safely. Using on-device **TensorFlow Lite Image Segmentation**, multi-zone spatial analysis, directional Text-To-Speech (TTS), tactile haptic feedback, and automotive-style reversing trajectory overlays, the app provides real-time guidance through open paths, obstacles, doorways, and tight spaces.

---

## Key Features

### On-Device Semantic Image Segmentation
* **TensorFlow Lite Models**: Real-time pixel-level semantic segmentation using models trained on **Cityscapes** and **PASCAL VOC** datasets (`shufflenetv2`, `deeplabv3`).
* **High-Speed Inference**: Direct output tensor evaluation on background threads with zero UI latency and optional TFLite GPU acceleration.

### Spatial Directional Guidance & Zone Analysis
* **5-Zone Corridor Scanning**: Subdivides the walking path into 5 scanning columns (*Far-Left*, *Mid-Left*, *Center*, *Mid-Right*, *Far-Right*) for precise directional guidance.
* **Spatial Spoken Feedback**: Announces directional hazard states:
  * *"Path clear ahead"*
  * *"Obstacle directly ahead"*
  * *"Obstacle on left"* / *"Obstacle on right"*
  * *"Path blocked"*

### Tight Space & Doorway Alignment Cues
* **Narrow Passage Detection**: Identifies narrow corridors, aisles, and doorways flanked by side walls or doorframes.
* **Micro-Alignment Prompts**: Spoken and haptic instructions to center the user through tight fits:
  * *"Narrow passage centered. Walk straight."*
  * *"Tight space. Shift left."*
  * *"Tight space. Shift right."*

### Tactile Haptic Vibration Feedback
* **Multimodal Vibration Patterns**: Delivers distinct tactile cues for quiet mode or noisy outdoor environments:
  * **Blocked / Severe Danger**: Double strong vibration pulse.
  * **Directional Obstacles**: Single medium pulse.
  * **Path Clear / Centered Doorway**: Gentle tick pulse.
  * **Dark / Obscured Camera**: Triple warning tick pulse.

### 🚗 Full-Screen Reversing Camera Trajectory Overlay
* **Visual Guidance Corridor**: Renders automotive-style perspective trajectory lines over the live camera view:
  * **Green Crossbar**: Far / Safe zone.
  * **Yellow Crossbar**: Mid / Caution zone.
  * **Red Crossbar**: Near / Stop zone.
  * **Translucent Red Hazard Shading**: Highlights blocked walking areas in real time.
* **Doorway Alignment Guide**: Dashed cyan overlay corridor highlighting narrow passage alignment.

### Blind Accessibility & Gestures
* **Full-Screen Touch Gestures**:
  * **Single Tap**: Instantly repeats current spatial status aloud on demand.
  * **Double Tap**: Toggles between **Voice + Haptic** mode and **Silent Haptic-Only** mode.
* **Low-Light & Lens Coverage Detection**: Continuously measures scene luminance, warning the user if the camera lens is covered or the environment is pitch black (*"Camera obscured or environment too dark"*).

---

## Technical Stack & Architecture

* **Language**: Kotlin & Java
* **Machine Learning**: TensorFlow Lite (`org.tensorflow:tensorflow-lite:2.12.0`)
* **Camera API**: Android Camera2 (`android.hardware.camera2`)
* **Audio & Speech**: Android Text-To-Speech (`android.speech.tts.TextToSpeech`)
* **Haptics**: Android Vibrator & VibrationEffect (`android.os.Vibrator`)
* **UI Layout**: ConstraintLayout with custom Kotlin `GuidanceOverlayView` overlay
* **Min SDK**: API 28 (Android 9.0) | **Target SDK**: API 34 (Android 14)

---

## Project Structure

```
Camera-segmentation-app/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/camera_segmentation_app/
│   │   │   │   ├── MainActivity.java            # Main activity, camera lifecycle, spatial analysis & TTS
│   │   │   │   ├── imageSegmenter.kt            # TFLite model loader, bytebuffer preprocessing & inference
│   │   │   │   ├── GuidanceOverlayView.kt       # Custom full-screen reversing trajectory overlay
│   │   │   │   └── Utils.kt                     # Image scaling and bitmap color mapping helpers
│   │   │   ├── assets/                          # Pre-trained TFLite model assets (.tflite)
│   │   │   └── res/layout/activity_main.xml     # Main layout with camera texture and overlays
│   │   └── AndroidManifest.xml                  # Permissions (CAMERA, VIBRATE, INTERNET)
│   └── build.gradle                             # App-level build dependencies
└── README.md
```

---

## Getting Started

### Prerequisites
* Android Studio (2021.1.1 or newer)
* Physical Android device (API 28+) with a working camera and vibrator
* Gradle 8.0+

### Setup & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/Camera-segmentation-app.git
   ```
2. Open the project directory in Android Studio.
3. Sync Gradle dependencies.
4. Connect a physical Android device with USB Debugging enabled.
5. Click **Run** or execute:
   ```bash
   ./gradlew app:installDebug
   ```

---

## License

This project is open source and available under the [MIT License](LICENSE).

---

## Acknowledgements
* [TensorFlow Lite](https://www.tensorflow.org/lite)
* Cityscapes & PASCAL VOC Segmentation Datasets
* PAUL CHOLA BWEMBYA MUMBI
