package com.example.lightdetector;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.Collections;

public final class MainActivity extends Activity {
    private static final int CAMERA_PERMISSION_REQUEST = 41;
    private static final int SENSITIVITY_MIN = 5;
    private static final int SENSITIVITY_MAX = 200;
    private static final int SENSITIVITY_STEP = 5;
    private static final int INTERVAL_MIN_MS = 100;
    private static final int INTERVAL_MAX_MS = 10000;
    private static final int INTERVAL_STEP_MS = 100;
    private static final long UI_UPDATE_INTERVAL_MS = 350L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BeepPlayer beepPlayer = new BeepPlayer();

    private TextView statusText;
    private TextView lightText;
    private TextView sensitivityValueText;
    private TextView intervalValueText;
    private SeekBar sensitivitySeekBar;
    private SeekBar intervalSeekBar;
    private CheckBox exposureLockCheckBox;
    private Button toggleButton;
    private Button permissionButton;

    private SharedPreferences preferences;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private ImageReader imageReader;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Surface captureSurface;
    private volatile boolean cameraActive;
    private volatile boolean exposureLockAvailable = true;
    private boolean userPaused;
    private long lastUiUpdateAt;
    private volatile double latestLuma;
    private int sensitivityPercent = 100;
    private int intervalMs = 1000;
    private boolean exposureLocked = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        configureSystemBars();
        bindViews();
        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        loadSettings();
        configureControls();
        updateSettingsText(false);
        updatePermissionState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCameraPermission() && !userPaused) {
            startDetection();
        }
    }

    @Override
    protected void onPause() {
        stopDetection(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        beepPlayer.stop();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_PERMISSION_REQUEST) {
            return;
        }
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            userPaused = false;
            updatePermissionState();
            startDetection();
        } else {
            userPaused = true;
            statusText.setText(R.string.status_permission_denied);
            permissionButton.setVisibility(View.VISIBLE);
            toggleButton.setText(R.string.start_detection);
            toggleButton.setEnabled(true);
        }
    }

    private void bindViews() {
        statusText = findViewById(R.id.statusText);
        lightText = findViewById(R.id.lightText);
        sensitivityValueText = findViewById(R.id.sensitivityValueText);
        intervalValueText = findViewById(R.id.intervalValueText);
        sensitivitySeekBar = findViewById(R.id.sensitivitySeekBar);
        intervalSeekBar = findViewById(R.id.intervalSeekBar);
        exposureLockCheckBox = findViewById(R.id.exposureLockCheckBox);
        toggleButton = findViewById(R.id.toggleButton);
        permissionButton = findViewById(R.id.permissionButton);
    }

    private void configureControls() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setVolumeControlStream(AudioManager.STREAM_ACCESSIBILITY);
        } else {
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
        }

        toggleButton.setOnClickListener(view -> {
            if (!hasCameraPermission()) {
                requestCameraPermission();
                return;
            }
            if (cameraActive) {
                userPaused = true;
                stopDetection(true);
                announce(getString(R.string.status_paused));
            } else {
                userPaused = false;
                startDetection();
            }
        });

        permissionButton.setOnClickListener(view -> requestCameraPermission());

        exposureLockCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            exposureLocked = isChecked;
            persistSettings();
            updateExposureLockText();
            updateCameraExposureLock();
            announce(getString(isChecked ? R.string.exposure_lock_on : R.string.exposure_lock_off));
        });

        sensitivitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sensitivityPercent = SENSITIVITY_MIN + progress;
                updateSettingsText(fromUser);
                persistSettings();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                announce(sensitivityValueText.getText().toString());
            }
        });

        intervalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                intervalMs = INTERVAL_MIN_MS + progress;
                updateSettingsText(fromUser);
                persistSettings();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                announce(intervalValueText.getText().toString());
            }
        });

        findViewById(R.id.sensitivityDownButton).setOnClickListener(
                view -> adjustSensitivity(-SENSITIVITY_STEP));
        findViewById(R.id.sensitivityUpButton).setOnClickListener(
                view -> adjustSensitivity(SENSITIVITY_STEP));
        findViewById(R.id.intervalDownButton).setOnClickListener(
                view -> adjustInterval(-INTERVAL_STEP_MS));
        findViewById(R.id.intervalUpButton).setOnClickListener(
                view -> adjustInterval(INTERVAL_STEP_MS));
    }

    private void loadSettings() {
        sensitivityPercent = clamp(
                preferences.getInt("sensitivityPercent", 100),
                SENSITIVITY_MIN,
                SENSITIVITY_MAX);
        intervalMs = clamp(
                preferences.getInt("intervalMs", 1000),
                INTERVAL_MIN_MS,
                INTERVAL_MAX_MS);
        exposureLocked = preferences.getBoolean("exposureLocked", true);
        sensitivitySeekBar.setProgress(sensitivityPercent - SENSITIVITY_MIN);
        intervalSeekBar.setProgress(intervalMs - INTERVAL_MIN_MS);
        exposureLockCheckBox.setChecked(exposureLocked);
    }

    private void persistSettings() {
        preferences.edit()
                .putInt("sensitivityPercent", sensitivityPercent)
                .putInt("intervalMs", intervalMs)
                .putBoolean("exposureLocked", exposureLocked)
                .apply();
        beepPlayer.setInput(latestLuma, sensitivityPercent, intervalMs);
    }

    private void adjustSensitivity(int delta) {
        int newValue = clamp(sensitivityPercent + delta, SENSITIVITY_MIN, SENSITIVITY_MAX);
        sensitivitySeekBar.setProgress(newValue - SENSITIVITY_MIN);
        announce(sensitivityValueText.getText().toString());
    }

    private void adjustInterval(int deltaMs) {
        int newValue = clamp(intervalMs + deltaMs, INTERVAL_MIN_MS, INTERVAL_MAX_MS);
        intervalSeekBar.setProgress(newValue - INTERVAL_MIN_MS);
        announce(intervalValueText.getText().toString());
    }

    private void updateSettingsText(boolean fromUser) {
        sensitivityValueText.setText(getString(R.string.sensitivity_value, sensitivityPercent));
        intervalValueText.setText(getString(R.string.interval_value, intervalMs));
        sensitivitySeekBar.setContentDescription(getString(
                R.string.sensitivity_label) + ", " + sensitivityPercent + " por ciento");
        intervalSeekBar.setContentDescription(getString(
                R.string.interval_label) + ", " + intervalMs + " milisegundos");
        if (fromUser) {
            beepPlayer.setInput(latestLuma, sensitivityPercent, intervalMs);
        }
        updateExposureLockText();
    }

    private void updateExposureLockText() {
        exposureLockCheckBox.setContentDescription(getString(
                exposureLocked ? R.string.exposure_lock_on : R.string.exposure_lock_off));
        exposureLockCheckBox.setEnabled(exposureLockAvailable);
        if (!exposureLockAvailable) {
            exposureLockCheckBox.setContentDescription(getString(R.string.exposure_lock_unavailable));
        }
    }

    private void updatePermissionState() {
        if (hasCameraPermission()) {
            permissionButton.setVisibility(View.GONE);
            toggleButton.setEnabled(true);
        } else {
            permissionButton.setVisibility(View.VISIBLE);
            toggleButton.setEnabled(true);
            statusText.setText(R.string.status_waiting_permission);
        }
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
    }

    private void startDetection() {
        if (cameraActive) {
            return;
        }
        if (!hasCameraPermission()) {
            updatePermissionState();
            requestCameraPermission();
            return;
        }

        startCameraThread();
        openCamera();
    }

    private void stopDetection(boolean updateUi) {
        beepPlayer.stop();
        closeCamera();
        stopCameraThread();
        cameraActive = false;
        if (updateUi) {
            statusText.setText(R.string.status_paused);
            toggleButton.setText(R.string.start_detection);
        }
    }

    private void startCameraThread() {
        if (cameraThread != null) {
            return;
        }
        cameraThread = new HandlerThread("LightDetectorCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopCameraThread() {
        if (cameraThread == null) {
            return;
        }
        cameraThread.quitSafely();
        try {
            cameraThread.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        cameraThread = null;
        cameraHandler = null;
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = findBackCameraId(cameraManager);
            if (cameraId == null) {
                statusText.setText(R.string.status_no_camera);
                stopCameraThread();
                return;
            }

            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Boolean aeLockAvailable = characteristics.get(
                    CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE);
            exposureLockAvailable = Boolean.TRUE.equals(aeLockAvailable);
            mainHandler.post(this::updateExposureLockText);

            Size size = chooseAnalysisSize(characteristics);
            imageReader = ImageReader.newInstance(
                    size.getWidth(),
                    size.getHeight(),
                    ImageFormat.YUV_420_888,
                    2);
            imageReader.setOnImageAvailableListener(this::onImageAvailable, cameraHandler);
            cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler);
        } catch (CameraAccessException | SecurityException exception) {
            showCameraError();
        }
    }

    private String findBackCameraId(CameraManager cameraManager) throws CameraAccessException {
        String fallbackCameraId = null;
        for (String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (fallbackCameraId == null) {
                fallbackCameraId = cameraId;
            }
            if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                return cameraId;
            }
        }
        return fallbackCameraId;
    }

    private Size chooseAnalysisSize(CameraCharacteristics characteristics) {
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            return new Size(640, 480);
        }

        Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
        if (sizes == null || sizes.length == 0) {
            return new Size(640, 480);
        }

        Size bestUnderLimit = null;
        int bestUnderLimitPixels = 0;
        Size smallest = sizes[0];
        int smallestPixels = smallest.getWidth() * smallest.getHeight();
        for (Size size : sizes) {
            int pixels = size.getWidth() * size.getHeight();
            if (pixels <= 640 * 480 && pixels > bestUnderLimitPixels) {
                bestUnderLimit = size;
                bestUnderLimitPixels = pixels;
            }
            if (pixels < smallestPixels) {
                smallest = size;
                smallestPixels = pixels;
            }
        }
        return bestUnderLimit != null ? bestUnderLimit : smallest;
    }

    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
            mainHandler.post(() -> stopDetection(true));
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            mainHandler.post(MainActivity.this::showCameraError);
        }
    };

    private void createCaptureSession() {
        try {
            Surface surface = imageReader.getSurface();
            captureSurface = surface;
            cameraDevice.createCaptureSession(
                    Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            captureSession = session;
                            startRepeatingCapture(surface);
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            mainHandler.post(MainActivity.this::showCameraError);
                        }
                    },
                    cameraHandler);
        } catch (CameraAccessException exception) {
            showCameraError();
        }
    }

    private void startRepeatingCapture(Surface surface) {
        try {
            applyRepeatingCapture(surface);
            cameraActive = true;
            beepPlayer.setInput(latestLuma, sensitivityPercent, intervalMs);
            beepPlayer.start();
            mainHandler.post(() -> {
                statusText.setText(R.string.status_active);
                toggleButton.setText(R.string.pause_detection);
                permissionButton.setVisibility(View.GONE);
                announce(getString(R.string.status_active));
            });
        } catch (CameraAccessException exception) {
            showCameraError();
        }
    }

    private void updateCameraExposureLock() {
        if (!cameraActive || cameraHandler == null) {
            return;
        }
        cameraHandler.post(() -> {
            try {
                if (cameraActive && captureSession != null && cameraDevice != null && captureSurface != null) {
                    applyRepeatingCapture(captureSurface);
                }
            } catch (CameraAccessException exception) {
                showCameraError();
            }
        });
    }

    private void applyRepeatingCapture(Surface surface) throws CameraAccessException {
        CaptureRequest.Builder request = cameraDevice.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW);
        request.addTarget(surface);
        request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        request.set(CaptureRequest.CONTROL_AE_LOCK, exposureLocked && exposureLockAvailable);
        request.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        captureSession.setRepeatingRequest(request.build(), null, cameraHandler);
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        captureSurface = null;
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) {
            return;
        }

        try {
            latestLuma = averageLuma(image);
            beepPlayer.setInput(latestLuma, sensitivityPercent, intervalMs);
            updateLightTextIfNeeded();
        } finally {
            image.close();
        }
    }

    private double averageLuma(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int width = image.getWidth();
        int height = image.getHeight();
        int stepX = Math.max(1, width / 64);
        int stepY = Math.max(1, height / 64);
        long sum = 0L;
        int count = 0;

        for (int y = 0; y < height; y += stepY) {
            int rowStart = y * rowStride;
            for (int x = 0; x < width; x += stepX) {
                int index = rowStart + (x * pixelStride);
                if (index < buffer.limit()) {
                    sum += buffer.get(index) & 0xFF;
                    count++;
                }
            }
        }

        return count == 0 ? 0.0 : (double) sum / count;
    }

    private void updateLightTextIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastUiUpdateAt < UI_UPDATE_INTERVAL_MS) {
            return;
        }
        lastUiUpdateAt = now;
        LightSignal signal = LightSignalMapper.fromLuma(latestLuma, sensitivityPercent);
        mainHandler.post(() -> lightText.setText(
                getString(R.string.light_value, signal.percent, signal.label)));
    }

    private void showCameraError() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::showCameraError);
            return;
        }
        beepPlayer.stop();
        closeCamera();
        stopCameraThread();
        cameraActive = false;
        statusText.setText(R.string.status_camera_error);
        toggleButton.setText(R.string.start_detection);
        permissionButton.setVisibility(hasCameraPermission() ? View.GONE : View.VISIBLE);
    }

    private void announce(String message) {
        View root = findViewById(R.id.root);
        if (root != null) {
            root.announceForAccessibility(message);
        }
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            applyInsets(view, insets);
            return insets;
        });
    }

    @SuppressWarnings("deprecation")
    private void applyInsets(View view, WindowInsets insets) {
        view.setPadding(
                insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
