package es.mcd3.lunapanoshooter;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class LunaAccessibilityService extends AccessibilityService {

    private static final int REFERENCE_WIDTH = 1200;
    private static final int REFERENCE_HEIGHT = 2000;
    private static final int TOTAL_PHOTOS = 31;

    private static final int JOY_X = 1056;
    private static final int JOY_Y = 1540;
    private static final int LEFT_X = 1012;
    private static final int RIGHT_X = 1100;
    private static final int UP_Y = 1496;
    private static final int DOWN_Y = 1584;
    private static final int SHUTTER_X = 600;
    private static final int SHUTTER_Y = 1832;

    private static final long MOVE_DURATION_MS = 500L;
    private static final long BETWEEN_MOVES_MS = 400L;
    private static final long SAVE_PHOTO_MS = 7000L;
    private static final long STABILIZE_ROW_MS = 5000L;
    private static final long BETWEEN_PHOTOS_MS = 2000L;

    private static final int[] STEPS_RIGHT = {2, 2, 2, 2, 2, 2, 3, 3};
    private static final int[] STEPS_LEFT = {3, 3, 2, 2, 2, 2, 2, 2};

    private static LunaAccessibilityService instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object pauseLock = new Object();

    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private View overlayView;
    private TextView statusText;
    private TextView progressText;
    private ProgressBar progressBar;
    private Button startButton;
    private Button pauseButton;
    private Button stopButton;

    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean cancelled;
    private Thread sequenceThread;
    private int photoNumber;

    public static boolean isConnected() {
        return instance != null;
    }

    public static void showControlOverlay() {
        LunaAccessibilityService service = instance;
        if (service != null) {
            service.mainHandler.post(service::showOverlay);
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showOverlay();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // The beta controls the camera using calibrated screen gestures.
    }

    @Override
    public void onInterrupt() {
        cancelSequence();
    }

    @Override
    public void onDestroy() {
        cancelSequence();
        removeOverlay();
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    private void showOverlay() {
        if (overlayView != null || windowManager == null) {
            return;
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(10));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(238, 24, 24, 24));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.argb(180, 255, 255, 255));
        panel.setBackground(background);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Luna PanoShooter");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15f);
        title.setPadding(dp(4), 0, dp(8), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button closeButton = compactButton("×");
        closeButton.setOnClickListener(v -> {
            if (!running) {
                removeOverlay();
            } else {
                updateStatus("Detén primero la captura");
            }
        });
        header.addView(closeButton, new LinearLayout.LayoutParams(dp(44), dp(42)));
        panel.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        statusText = new TextView(this);
        statusText.setText("Preparado · coloca la cámara en el centro");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(14f);
        statusText.setPadding(dp(4), dp(5), dp(4), dp(4));
        panel.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        progressText = new TextView(this);
        progressText.setText("0 / 31");
        progressText.setTextColor(Color.LTGRAY);
        progressText.setTextSize(13f);
        progressText.setGravity(Gravity.END);
        panel.addView(progressText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(TOTAL_PHOTOS);
        progressBar.setProgress(0);
        panel.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(18)
        ));

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);

        startButton = compactButton("INICIAR");
        startButton.setOnClickListener(v -> startSequence());
        buttonRow.addView(startButton, weightedButtonParams());

        pauseButton = compactButton("PAUSA");
        pauseButton.setEnabled(false);
        pauseButton.setOnClickListener(v -> togglePause());
        buttonRow.addView(pauseButton, weightedButtonParams());

        stopButton = compactButton("DETENER");
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> cancelSequence());
        buttonRow.addView(stopButton, weightedButtonParams());

        panel.addView(buttonRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        overlayParams = new WindowManager.LayoutParams(
                dp(330),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.x = dp(10);
        overlayParams.y = dp(90);

        final float[] dragStart = new float[2];
        final int[] windowStart = new int[2];
        header.setOnTouchListener((v, event) -> {
            if (overlayParams == null || windowManager == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragStart[0] = event.getRawX();
                    dragStart[1] = event.getRawY();
                    windowStart[0] = overlayParams.x;
                    windowStart[1] = overlayParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    overlayParams.x = windowStart[0] + Math.round(event.getRawX() - dragStart[0]);
                    overlayParams.y = windowStart[1] + Math.round(event.getRawY() - dragStart[1]);
                    windowManager.updateViewLayout(panel, overlayParams);
                    return true;
                default:
                    return false;
            }
        });

        overlayView = panel;
        windowManager.addView(panel, overlayParams);
    }

    private Button compactButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12f);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(6), 0, dp(6), 0);
        return button;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(2), dp(4), dp(2), 0);
        return params;
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (RuntimeException ignored) {
                // The system may already have removed the accessibility overlay.
            }
        }
        overlayView = null;
        overlayParams = null;
        statusText = null;
        progressText = null;
        progressBar = null;
        startButton = null;
        pauseButton = null;
        stopButton = null;
    }

    private void startSequence() {
        if (running) {
            return;
        }

        DisplayMetrics metrics = realDisplayMetrics();
        if (metrics.widthPixels >= metrics.heightPixels) {
            updateStatus("Pon la tablet en vertical antes de iniciar");
            return;
        }

        running = true;
        paused = false;
        cancelled = false;
        photoNumber = 0;
        updateControls();
        updateProgress("Preparando", 0);

        sequenceThread = new Thread(() -> {
            try {
                runCaptureSequence();
                if (!cancelled) {
                    updateStatus("Captura terminada · 31 fotografías");
                }
            } catch (SequenceCancelledException ignored) {
                updateStatus("Captura detenida en la foto " + photoNumber);
            } catch (Exception error) {
                updateStatus("Error: " + error.getMessage());
            } finally {
                running = false;
                paused = false;
                sequenceThread = null;
                updateControls();
            }
        }, "LunaPanoShooterSequence");
        sequenceThread.start();
    }

    private void togglePause() {
        if (!running) {
            return;
        }

        synchronized (pauseLock) {
            paused = !paused;
            if (!paused) {
                pauseLock.notifyAll();
            }
        }

        if (paused) {
            updateStatus("En pausa");
        } else {
            updateStatus("Reanudando");
        }
        updateControls();
    }

    private void cancelSequence() {
        if (!running) {
            return;
        }

        cancelled = true;
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
        updateStatus("Deteniendo…");
    }

    private void runCaptureSequence() throws Exception {
        for (int seconds = 10; seconds >= 1; seconds--) {
            updateStatus("Comienza en " + seconds + " s · no toques la cámara");
            controlledSleep(1000L);
        }

        updateStatus("Fila central · yendo a la izquierda");
        move(Direction.LEFT, 4);
        controlledSleep(STABILIZE_ROW_MS);
        captureRow("Central", Direction.RIGHT, STEPS_RIGHT);

        updateStatus("Fila superior · subiendo 3 pasos");
        move(Direction.UP, 3);
        controlledSleep(STABILIZE_ROW_MS);
        captureRow("Superior", Direction.LEFT, STEPS_LEFT);

        updateStatus("Cenit · volviendo al centro horizontal");
        move(Direction.RIGHT, 4);
        controlledSleep(3000L);

        updateStatus("Cenit · subiendo al límite");
        move(Direction.UP, 5);
        controlledSleep(STABILIZE_ROW_MS);

        move(Direction.LEFT, 3);
        controlledSleep(3000L);
        takePhoto("Cenit −3");

        move(Direction.RIGHT, 2);
        controlledSleep(3000L);
        takePhoto("Cenit −1");

        move(Direction.RIGHT, 2);
        controlledSleep(3000L);
        takePhoto("Cenit +1");

        move(Direction.RIGHT, 2);
        controlledSleep(3000L);
        takePhoto("Cenit +3");

        updateStatus("Fila inferior · bajando 14 pasos");
        move(Direction.DOWN, 14);

        updateStatus("Fila inferior · yendo a la izquierda");
        move(Direction.LEFT, 7);
        controlledSleep(STABILIZE_ROW_MS);
        captureRow("Inferior", Direction.RIGHT, STEPS_RIGHT);
    }

    private void captureRow(String rowName, Direction direction, int[] steps) throws Exception {
        for (int index = 0; index < 9; index++) {
            takePhoto(rowName + " " + (index + 1) + "/9");

            if (index < 8) {
                move(direction, steps[index]);
                controlledSleep(BETWEEN_PHOTOS_MS);
            }
        }
    }

    private void takePhoto(String label) throws Exception {
        photoNumber++;
        updateProgress(label, photoNumber);
        tap(SHUTTER_X, SHUTTER_Y);
        controlledSleep(SAVE_PHOTO_MS);
    }

    private void move(Direction direction, int count) throws Exception {
        for (int index = 0; index < count; index++) {
            checkRunningState();

            switch (direction) {
                case LEFT:
                    swipe(JOY_X, JOY_Y, LEFT_X, JOY_Y, MOVE_DURATION_MS);
                    break;
                case RIGHT:
                    swipe(JOY_X, JOY_Y, RIGHT_X, JOY_Y, MOVE_DURATION_MS);
                    break;
                case UP:
                    swipe(JOY_X, JOY_Y, JOY_X, UP_Y, MOVE_DURATION_MS);
                    break;
                case DOWN:
                    swipe(JOY_X, JOY_Y, JOY_X, DOWN_Y, MOVE_DURATION_MS);
                    break;
            }

            controlledSleep(BETWEEN_MOVES_MS);
        }
    }

    private void tap(int referenceX, int referenceY) throws Exception {
        checkRunningState();
        DisplayMetrics metrics = realDisplayMetrics();
        float x = scaleX(referenceX, metrics);
        float y = scaleY(referenceY, metrics);

        Path path = new Path();
        path.moveTo(x, y);
        dispatchGestureAndWait(path, 80L);
    }

    private void swipe(int startReferenceX, int startReferenceY,
                       int endReferenceX, int endReferenceY,
                       long durationMs) throws Exception {
        checkRunningState();
        DisplayMetrics metrics = realDisplayMetrics();

        Path path = new Path();
        path.moveTo(scaleX(startReferenceX, metrics), scaleY(startReferenceY, metrics));
        path.lineTo(scaleX(endReferenceX, metrics), scaleY(endReferenceY, metrics));
        dispatchGestureAndWait(path, durationMs);
    }

    private void dispatchGestureAndWait(Path path, long durationMs) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] dispatched = {false};

        mainHandler.post(() -> {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0L, durationMs));

            dispatched[0] = dispatchGesture(
                    builder.build(),
                    new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            latch.countDown();
                        }

                        @Override
                        public void onCancelled(GestureDescription gestureDescription) {
                            latch.countDown();
                        }
                    },
                    null
            );

            if (!dispatched[0]) {
                latch.countDown();
            }
        });

        boolean finished = latch.await(durationMs + 2500L, TimeUnit.MILLISECONDS);
        if (!finished || !dispatched[0]) {
            throw new IllegalStateException("Android no pudo ejecutar un gesto");
        }
    }

    private void controlledSleep(long durationMs) throws Exception {
        long remaining = durationMs;
        long last = System.currentTimeMillis();

        while (remaining > 0L) {
            checkRunningState();
            long slice = Math.min(remaining, 100L);
            Thread.sleep(slice);
            long now = System.currentTimeMillis();
            if (!paused) {
                remaining -= Math.max(1L, now - last);
            }
            last = now;
        }
    }

    private void checkRunningState() throws SequenceCancelledException, InterruptedException {
        if (cancelled) {
            throw new SequenceCancelledException();
        }

        synchronized (pauseLock) {
            while (paused && !cancelled) {
                pauseLock.wait(250L);
            }
        }

        if (cancelled) {
            throw new SequenceCancelledException();
        }
    }

    private DisplayMetrics realDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics.setTo(getResources().getDisplayMetrics());
        }
        return metrics;
    }

    private float scaleX(int referenceX, DisplayMetrics metrics) {
        return referenceX * (metrics.widthPixels / (float) REFERENCE_WIDTH);
    }

    private float scaleY(int referenceY, DisplayMetrics metrics) {
        return referenceY * (metrics.heightPixels / (float) REFERENCE_HEIGHT);
    }

    private void updateStatus(String text) {
        mainHandler.post(() -> {
            if (statusText != null) {
                statusText.setText(text);
            }
        });
    }

    private void updateProgress(String label, int progress) {
        mainHandler.post(() -> {
            if (statusText != null) {
                statusText.setText(label);
            }
            if (progressText != null) {
                progressText.setText(progress + " / " + TOTAL_PHOTOS);
            }
            if (progressBar != null) {
                progressBar.setProgress(progress);
            }
        });
    }

    private void updateControls() {
        mainHandler.post(() -> {
            if (startButton != null) {
                startButton.setEnabled(!running);
            }
            if (pauseButton != null) {
                pauseButton.setEnabled(running);
                pauseButton.setText(paused ? "SEGUIR" : "PAUSA");
            }
            if (stopButton != null) {
                stopButton.setEnabled(running);
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private enum Direction {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    private static final class SequenceCancelledException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
