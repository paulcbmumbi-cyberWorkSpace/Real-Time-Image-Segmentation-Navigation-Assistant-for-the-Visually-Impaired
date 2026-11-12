package com.example.camera_segmentation_app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    public static final int STATE_ALL_CLEAR = 1;
    public static final int STATE_OBSTACLE_CENTER = 2;
    public static final int STATE_OBSTACLE_LEFT = 3;
    public static final int STATE_OBSTACLE_RIGHT = 4;
    public static final int STATE_BLOCKED = 5;
    public static final int STATE_TOO_DARK = 6;
    public static final int STATE_NARROW_PASSAGE_CENTER = 7;
    public static final int STATE_NARROW_PASSAGE_SHIFT_LEFT = 8;
    public static final int STATE_NARROW_PASSAGE_SHIFT_RIGHT = 9;

    private TextToSpeech mTTs;
    private ImageSegmenter mSegmenter;
    private Vibrator mVibrator;
    private GestureDetector mGestureDetector;
    private GuidanceOverlayView mGuidanceOverlay;
    private Bitmap mReusableOutputBitmap;

    private String mLastSpokenLine = "";
    private int mCurrentGuidanceState = STATE_ALL_CLEAR;
    private boolean mHapticOnlyMode = false;
    private int mSensorRotation = 90; // Default portrait camera sensor rotation angle

    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private TextureView mPreview;
    private ImageView mMiniView;

    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private Runnable mSegmentationRunnable;

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            setupCamera(width, height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}
    };

    private CameraDevice mCamera;
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCamera = camera;
            startPreview();
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Camera connected successfully!", Toast.LENGTH_SHORT).show());
            startSegmentationLoop();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            stopSegmentationLoop();
            camera.close();
            mCamera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int i) {
            stopSegmentationLoop();
            camera.close();
            mCamera = null;
        }
    };

    private String mCameraID;
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private Size mPreviewSize;
    private CaptureRequest.Builder mCaptureRequestBuilder;

    private static class CompareSizeByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) (lhs.getWidth() * lhs.getHeight()) -
                    (long) (rhs.getWidth() * rhs.getHeight()));
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPreview = findViewById(R.id.PreviewView);
        mMiniView = findViewById(R.id.MiniView);
        mGuidanceOverlay = findViewById(R.id.GuidanceOverlay);

        // Initialize ImageSegmenter & Vibrator
        mSegmenter = new ImageSegmenter(this);
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        mTTs = new TextToSpeech(getApplicationContext(), i -> {
            if (i != TextToSpeech.ERROR) {
                mTTs.setLanguage(Locale.US);
            } else {
                Toast.makeText(MainActivity.this, "TTS Initialization Error", Toast.LENGTH_SHORT).show();
            }
        });

        setupTouchGestures();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchGestures() {
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                speakStatusImmediate(mCurrentGuidanceState);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                mHapticOnlyMode = !mHapticOnlyMode;
                String msg = mHapticOnlyMode ? "Vibration only mode activated." : "Voice guidance mode activated.";
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                if (mTTs != null) {
                    mTTs.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "MODE_TOGGLE");
                }
                triggerHapticFeedback(STATE_ALL_CLEAR);
                return true;
            }
        });

        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.setOnTouchListener((v, event) -> mGestureDetector.onTouchEvent(event));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();

        if (mPreview.isAvailable()) {
            setupCamera(mPreview.getWidth(), mPreview.getHeight());
            connectCamera();
        } else {
            mPreview.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        stopSegmentationLoop();
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopSegmentationLoop();
        if (mReusableOutputBitmap != null && !mReusableOutputBitmap.isRecycled()) {
            mReusableOutputBitmap.recycle();
            mReusableOutputBitmap = null;
        }
        if (mSegmenter != null) {
            mSegmenter.close();
            mSegmenter = null;
        }
        if (mTTs != null) {
            mTTs.stop();
            mTTs.shutdown();
            mTTs = null;
        }
        super.onDestroy();
    }

    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("CameraBackground");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (mBackgroundHandlerThread != null) {
            mBackgroundHandlerThread.quitSafely();
            try {
                mBackgroundHandlerThread.join();
                mBackgroundHandlerThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void startSegmentationLoop() {
        stopSegmentationLoop();
        mSegmentationRunnable = new Runnable() {
            @Override
            public void run() {
                if (mCamera == null || mPreview == null || !mPreview.isAvailable() || mSegmenter == null) {
                    return;
                }

                // Get camera frame on UI thread to ensure thread safety with TextureView
                runOnUiThread(() -> {
                    if (mPreview != null && mPreview.isAvailable()) {
                        Bitmap rawInput = mPreview.getBitmap(225, 225);
                        if (rawInput != null && mBackgroundHandler != null) {
                            mBackgroundHandler.post(() -> processSegmentationFrame(rawInput));
                        }
                    }
                });

                // Schedule next capture iteration (250ms = 4 FPS for real-time responsiveness)
                if (mBackgroundHandler != null) {
                    mBackgroundHandler.postDelayed(this, 250);
                }
            }
        };

        if (mBackgroundHandler != null) {
            mBackgroundHandler.postDelayed(mSegmentationRunnable, 250);
        }
    }

    private void stopSegmentationLoop() {
        if (mBackgroundHandler != null && mSegmentationRunnable != null) {
            mBackgroundHandler.removeCallbacks(mSegmentationRunnable);
        }
    }

    private Bitmap rotateBitmap(Bitmap source, float angle) {
        if (angle == 0) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        Bitmap rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        source.recycle();
        return rotated;
    }

    private void processSegmentationFrame(Bitmap rawImg) {
        if (mSegmenter == null || isFinishing() || isDestroyed()) {
            rawImg.recycle();
            return;
        }

        // Rotate bitmap by camera sensor orientation angle to make frame upright for neural network
        Bitmap img = rotateBitmap(rawImg, mSensorRotation);

        int width = img.getWidth();
        int height = img.getHeight();

        // 1. Calculate average luminance to detect low light / camera occlusion
        int avgLuminance = calculateAverageLuminance(img);

        // 2. Perform TFLite Inference directly on Camera Frame (Background Thread)
        int[] outputArray = mSegmenter.segmentFrame(img);
        img.recycle();

        // 3. Directly Analyze Raw Segmentation Output Tensor Array (Dataset-aware)
        final int guidanceState = analyzeEnvironment(outputArray, width, height, avgLuminance);

        // 4. Render segmentation mask preview with bitmap reuse
        if (mReusableOutputBitmap == null || mReusableOutputBitmap.getWidth() != width || mReusableOutputBitmap.getHeight() != height) {
            mReusableOutputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        ImageSegmenter.Model model = mSegmenter.getCurrentModel();
        Utils.Companion.segmentResultToBitmap(outputArray, model.getColors(), mReusableOutputBitmap);

        // 5. Update Full-Screen Camera Guidance Overlay, Inset View, Haptic & Voice feedback on Main Thread
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                if (mGuidanceOverlay != null) {
                    mGuidanceOverlay.setGuidanceState(guidanceState);
                }
                if (mMiniView != null && mReusableOutputBitmap != null) {
                    mMiniView.setImageBitmap(mReusableOutputBitmap);
                }
                voiceOutput(guidanceState);
            }
        });
    }

    private int calculateAverageLuminance(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        long sumLuminance = 0;
        int sampledCount = 0;
        for (int i = 0; i < pixels.length; i += 4) { // sample every 4th pixel for high speed
            int color = pixels[i];
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            sumLuminance += (long) (0.299 * r + 0.587 * g + 0.114 * b);
            sampledCount++;
        }
        return sampledCount > 0 ? (int) (sumLuminance / sampledCount) : 128;
    }

    public int analyzeEnvironment(int[] outputArray, int width, int height, int avgLuminance) {
        if (avgLuminance < 15) {
            return STATE_TOO_DARK;
        }

        ImageSegmenter.Model model = mSegmenter != null ? mSegmenter.getCurrentModel() : null;
        ImageSegmenter.Model.Dataset dataset = model != null ? model.getColorSchema() : ImageSegmenter.Model.Dataset.CITYSCAPES;

        int col1Count = 0; // Far-Left (0..20%)
        int col2Count = 0; // Mid-Left (20..40%)
        int col3Count = 0; // Center (40..60%)
        int col4Count = 0; // Mid-Right (60..80%)
        int col5Count = 0; // Far-Right (80..100%)
        int sampledPerCol = 0;

        // Sample lower half of 225x225 grid (y: height/2 to height)
        for (int y = height / 2; y < height; y += 4) {
            for (int x = 0; x < width; x += 4) {
                int index = y * width + x;
                if (index < 0 || index >= outputArray.length) continue;
                int classIndex = outputArray[index];

                boolean isFloor;
                if (dataset == ImageSegmenter.Model.Dataset.PASCAL) {
                    // PASCAL VOC: Class 0 = Background/Ground
                    isFloor = (classIndex == 0);
                } else {
                    // CITYSCAPES: Class 0 = Road, Class 1 = Sidewalk, Class 9 = Vegetation/Grass
                    isFloor = (classIndex == 0 || classIndex == 1 || classIndex == 9);
                }

                float xRatio = (float) x / width;
                if (xRatio < 0.20f) {
                    if (isFloor) col1Count++;
                    sampledPerCol++;
                } else if (xRatio < 0.40f) {
                    if (isFloor) col2Count++;
                } else if (xRatio < 0.60f) {
                    if (isFloor) col3Count++;
                } else if (xRatio < 0.80f) {
                    if (isFloor) col4Count++;
                } else {
                    if (isFloor) col5Count++;
                }
            }
        }

        double threshold = sampledPerCol * 0.45; // At least 45% of column must be clear floor
        boolean c1 = col1Count >= threshold;
        boolean c2 = col2Count >= threshold;
        boolean c3 = col3Count >= threshold;
        boolean c4 = col4Count >= threshold;
        boolean c5 = col5Count >= threshold;

        if (c1 && c2 && c3 && c4 && c5) {
            return STATE_ALL_CLEAR;
        }
        if (!c1 && !c2 && !c3 && !c4 && !c5) {
            return STATE_BLOCKED;
        }

        if (!c3) {
            return STATE_OBSTACLE_CENTER;
        }

        // Side narrow passage / doorway check
        if (!c1 || !c5) {
            if (c2 && c4) {
                return STATE_NARROW_PASSAGE_CENTER;
            } else if (c2) {
                return STATE_NARROW_PASSAGE_SHIFT_LEFT;
            } else if (c4) {
                return STATE_NARROW_PASSAGE_SHIFT_RIGHT;
            }
        }

        if (!c2) {
            return STATE_OBSTACLE_LEFT;
        } else if (!c4) {
            return STATE_OBSTACLE_RIGHT;
        }

        return STATE_ALL_CLEAR;
    }

    private String getGuidanceText(int state) {
        switch (state) {
            case STATE_ALL_CLEAR:
                return "Path clear ahead.";
            case STATE_OBSTACLE_CENTER:
                return "Obstacle directly ahead.";
            case STATE_OBSTACLE_LEFT:
                return "Obstacle on left.";
            case STATE_OBSTACLE_RIGHT:
                return "Obstacle on right.";
            case STATE_BLOCKED:
                return "Path blocked.";
            case STATE_TOO_DARK:
                return "Camera obscured or environment too dark.";
            case STATE_NARROW_PASSAGE_CENTER:
                return "Narrow passage centered. Walk straight.";
            case STATE_NARROW_PASSAGE_SHIFT_LEFT:
                return "Tight space. Shift left.";
            case STATE_NARROW_PASSAGE_SHIFT_RIGHT:
                return "Tight space. Shift right.";
            default:
                return "";
        }
    }

    private void voiceOutput(int state) {
        mCurrentGuidanceState = state;
        triggerHapticFeedback(state);

        if (mHapticOnlyMode) {
            return; // In haptic-only mode, skip periodic voice output
        }

        String lineToSpeak = getGuidanceText(state);
        if (lineToSpeak.isEmpty()) return;

        // Speak if state changed or TTS is not currently speaking
        if (mTTs != null && (!lineToSpeak.equals(mLastSpokenLine) || !mTTs.isSpeaking())) {
            mLastSpokenLine = lineToSpeak;
            Toast.makeText(MainActivity.this, lineToSpeak, Toast.LENGTH_SHORT).show();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mTTs.speak(lineToSpeak, TextToSpeech.QUEUE_FLUSH, null, "GUIDANCE");
            } else {
                mTTs.speak(lineToSpeak, TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }

    private void speakStatusImmediate(int state) {
        String lineToSpeak = getGuidanceText(state);
        if (lineToSpeak.isEmpty()) return;
        Toast.makeText(MainActivity.this, lineToSpeak, Toast.LENGTH_SHORT).show();
        if (mTTs != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mTTs.speak(lineToSpeak, TextToSpeech.QUEUE_FLUSH, null, "IMMEDIATE");
            } else {
                mTTs.speak(lineToSpeak, TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }

    private void triggerHapticFeedback(int guidanceState) {
        if (mVibrator == null || !mVibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                switch (guidanceState) {
                    case STATE_BLOCKED:
                        // Double vibration pulse for blocked path
                        long[] timings = {0, 150, 100, 150};
                        int[] amplitudes = {0, 255, 0, 255};
                        mVibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
                        break;
                    case STATE_OBSTACLE_CENTER:
                    case STATE_OBSTACLE_LEFT:
                    case STATE_OBSTACLE_RIGHT:
                        // Single medium pulse for directional obstacle
                        mVibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                        break;
                    case STATE_TOO_DARK:
                        // Three short warning ticks
                        mVibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 50, 50, 50, 50, 50}, -1));
                        break;
                    case STATE_NARROW_PASSAGE_CENTER:
                        // Gentle tick for centered narrow passage
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            mVibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.EFFECT_TICK));
                        } else {
                            mVibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
                        }
                        break;
                    case STATE_NARROW_PASSAGE_SHIFT_LEFT:
                    case STATE_NARROW_PASSAGE_SHIFT_RIGHT:
                        // Short double tick to prompt shift
                        long[] shiftTimings = {0, 40, 60, 40};
                        mVibrator.vibrate(VibrationEffect.createWaveform(shiftTimings, -1));
                        break;
                    case STATE_ALL_CLEAR:
                        // Subtle tick for clear path
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            mVibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.EFFECT_TICK));
                        } else {
                            mVibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                        }
                        break;
                }
            } else {
                if (guidanceState == STATE_BLOCKED) {
                    mVibrator.vibrate(300);
                } else if (guidanceState != STATE_ALL_CLEAR) {
                    mVibrator.vibrate(100);
                }
            }
        } catch (Exception e) {
            Log.e("HAPTIC", "Vibration error", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "Application won't run without Permissions.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if (hasFocus) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraID : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraID);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                mSensorRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
                boolean swapRotation = mSensorRotation == 90 || mSensorRotation == 270;
                int rotatedWidth = width;
                int rotatedHeight = height;
                if (swapRotation) {
                    rotatedWidth = height;
                    rotatedHeight = width;
                }
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                mCameraID = cameraID;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mBackgroundHandler);
                } else {
                    if (shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                        Toast.makeText(this, "Video app required access to camera", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT);
                }
            } else {
                cameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        SurfaceTexture surfaceTexture = mPreview.getSurfaceTexture();
        if (surfaceTexture == null || mPreviewSize == null) return;
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            mCaptureRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            mCamera.createCaptureSession(Arrays.asList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                session.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(getApplicationContext(), "Unable to setup Preview", Toast.LENGTH_SHORT).show();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else {
            return choices[0];
        }
    }
}
