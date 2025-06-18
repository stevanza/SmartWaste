package com.example.smartwaste;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.smartwaste.api.RoboflowAPI;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 101;
    private static final long FRAME_ANALYSIS_INTERVAL_MS = 1500;
    private static final int INPUT_SIZE = 416;

    private PreviewView previewView;
    private BoundingBoxOverlay boundingBoxOverlay;
    private TextView tvResult, tvStatus;
    private ProgressBar progressBar;
    private ImageView debugImageView;
    private RoboflowAPI roboflowAPI;
    private ExecutorService cameraExecutor;

    private final AtomicBoolean isDetecting = new AtomicBoolean(false);
    private long lastAnalyzedTimestamp = 0;

    // Helper class to hold processing results
    private static class ProcessedImageResult {
        Bitmap bitmap;
        int inferenceWidth;
        int inferenceHeight;
        int rotationDegrees;

        ProcessedImageResult(Bitmap bitmap, int width, int height, int rotationDegrees) {
            this.bitmap = bitmap;
            this.inferenceWidth = width;
            this.inferenceHeight = height;
            this.rotationDegrees = rotationDegrees;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        roboflowAPI = new RoboflowAPI();
        cameraExecutor = Executors.newSingleThreadExecutor();

        checkCameraPermission();
    }

    private void initViews() {
        previewView = findViewById(R.id.previewView);
        boundingBoxOverlay = findViewById(R.id.boundingBoxOverlay);
        tvResult = findViewById(R.id.tvResult);
        tvStatus = findViewById(R.id.tvStatus);
        progressBar = findViewById(R.id.progressBar);
        debugImageView = findViewById(R.id.debugImageView);
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Izin kamera ditolak.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 640))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    try {
                        if (System.currentTimeMillis() - lastAnalyzedTimestamp < FRAME_ANALYSIS_INTERVAL_MS || !isDetecting.compareAndSet(false, true)) {
                            return;
                        }

                        lastAnalyzedTimestamp = System.currentTimeMillis();

                        Bitmap bitmap = toBitmap(imageProxy);
                        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

                        if (bitmap != null) {
                            ProcessedImageResult result = processImage(bitmap, rotationDegrees);
                            detectGarbage(result);
                        } else {
                            isDetecting.set(false);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Terjadi error fatal di dalam analyzer", e);
                        isDetecting.set(false);
                    } finally {
                        imageProxy.close();
                    }
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (Exception e) {
                Log.e(TAG, "Gagal memulai kamera.", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void detectGarbage(ProcessedImageResult processedImageResult) {
        if (processedImageResult == null) {
            isDetecting.set(false);
            return;
        }

        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            tvStatus.setText(R.string.analyzing);
            if(debugImageView != null) {
                debugImageView.setImageBitmap(processedImageResult.bitmap);
            }
        });

        String base64Image = bitmapToBase64(processedImageResult.bitmap);
        roboflowAPI.detectGarbage(base64Image, new RoboflowAPI.ApiCallback() {
            @Override
            public void onSuccess(List<RoboflowAPI.Prediction> predictions) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("Arahkan kamera ke sampah...");
                    if(boundingBoxOverlay != null) {
                        int originalWidth = processedImageResult.inferenceWidth;
                        int originalHeight = processedImageResult.inferenceHeight;

                        if (processedImageResult.rotationDegrees == 90 || processedImageResult.rotationDegrees == 270) {
                            originalWidth = processedImageResult.inferenceHeight;
                            originalHeight = processedImageResult.inferenceWidth;
                        }

                        boundingBoxOverlay.setPredictions(predictions, originalWidth, originalHeight, processedImageResult.rotationDegrees);
                    }
                    if(tvResult != null) {
                        tvResult.setText(buildSummaryString(predictions));
                    }
                    isDetecting.set(false);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("Terjadi kesalahan, mencoba lagi...");
                    if(boundingBoxOverlay != null) {
                        boundingBoxOverlay.setPredictions(null, 1, 1, 0);
                    }
                    isDetecting.set(false);
                });
            }
        });
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream);
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
    }

    private ProcessedImageResult processImage(Bitmap bitmap, int rotationDegrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Hitung rasio aspek dari gambar yang sudah diputar
        float aspectRatio = (float) rotatedBitmap.getWidth() / (float) rotatedBitmap.getHeight();

        int finalWidth = INPUT_SIZE;
        int finalHeight = INPUT_SIZE;

        // Sesuaikan lebar atau tinggi untuk menjaga rasio aspek
        if (rotatedBitmap.getWidth() > rotatedBitmap.getHeight()) {
            finalHeight = (int) (INPUT_SIZE / aspectRatio);
        } else {
            finalWidth = (int) (INPUT_SIZE * aspectRatio);
        }

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(rotatedBitmap, finalWidth, finalHeight, true);
        return new ProcessedImageResult(resizedBitmap, finalWidth, finalHeight, rotationDegrees);
    }

    // *** SOLUSI OPTIMAL: MENANGANI PIXEL STRIDE DAN ROW STRIDE DENGAN BENAR ***
    private Bitmap toBitmap(ImageProxy image) {
        try {
            if (image.getFormat() != ImageFormat.YUV_420_888) {
                Log.e(TAG, "Unsupported format: " + image.getFormat());
                return null;
            }

            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ImageProxy.PlaneProxy yPlane = planes[0];
            ImageProxy.PlaneProxy uPlane = planes[1];
            ImageProxy.PlaneProxy vPlane = planes[2];

            int width = image.getWidth();
            int height = image.getHeight();

            // Ambil informasi stride
            int yRowStride = yPlane.getRowStride();
            int yPixelStride = yPlane.getPixelStride();
            int uvRowStride = uPlane.getRowStride();
            int uvPixelStride = uPlane.getPixelStride();

            Log.d(TAG, String.format("Image: %dx%d, Y stride: row=%d pixel=%d, UV stride: row=%d pixel=%d",
                    width, height, yRowStride, yPixelStride, uvRowStride, uvPixelStride));

            // Buffer untuk setiap plane
            ByteBuffer yBuffer = yPlane.getBuffer();
            ByteBuffer uBuffer = uPlane.getBuffer();
            ByteBuffer vBuffer = vPlane.getBuffer();

            // *** METODE 1: KONVERSI DENGAN STRIDE HANDLING ***
            byte[] nv21 = new byte[width * height + 2 * ((width + 1) / 2) * ((height + 1) / 2)];

            // Copy Y plane dengan mempertimbangkan row stride
            int yIndex = 0;
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int bufferIndex = row * yRowStride + col * yPixelStride;
                    if (bufferIndex < yBuffer.remaining()) {
                        nv21[yIndex++] = yBuffer.get(bufferIndex);
                    }
                }
            }

            // Copy UV planes untuk NV21 format (VUVU...)
            int uvIndex = width * height;
            for (int row = 0; row < height / 2; row++) {
                for (int col = 0; col < width / 2; col++) {
                    int bufferIndex = row * uvRowStride + col * uvPixelStride;

                    // V kemudian U untuk NV21
                    if (bufferIndex < vBuffer.remaining()) {
                        nv21[uvIndex++] = vBuffer.get(bufferIndex);
                    }
                    if (bufferIndex < uBuffer.remaining()) {
                        nv21[uvIndex++] = uBuffer.get(bufferIndex);
                    }
                }
            }

            // Konversi ke JPEG
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean success = yuvImage.compressToJpeg(new Rect(0, 0, width, height), 85, out);

            if (!success) {
                Log.w(TAG, "NV21 method failed, trying alternative method");
                return toBitmapAlternative(image);
            }

            byte[] jpegData = out.toByteArray();
            out.close();

            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
            if (bitmap != null) {
                Log.d(TAG, "Successfully converted using NV21 method: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                return bitmap;
            } else {
                Log.w(TAG, "JPEG decode failed, trying alternative method");
                return toBitmapAlternative(image);
            }

        } catch (Exception e) {
            Log.e(TAG, "Main conversion failed, trying alternative", e);
            return toBitmapAlternative(image);
        }
    }

    // *** METODE ALTERNATIF: RGB CONVERSION LANGSUNG ***
    private Bitmap toBitmapAlternative(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ImageProxy.PlaneProxy yPlane = planes[0];
            ImageProxy.PlaneProxy uPlane = planes[1];
            ImageProxy.PlaneProxy vPlane = planes[2];

            int width = image.getWidth();
            int height = image.getHeight();

            ByteBuffer yBuffer = yPlane.getBuffer();
            ByteBuffer uBuffer = uPlane.getBuffer();
            ByteBuffer vBuffer = vPlane.getBuffer();

            int yRowStride = yPlane.getRowStride();
            int yPixelStride = yPlane.getPixelStride();
            int uvRowStride = uPlane.getRowStride();
            int uvPixelStride = uPlane.getPixelStride();

            int[] pixels = new int[width * height];

            // Konversi YUV420 ke RGB dengan stride handling
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int yIndex = y * yRowStride + x * yPixelStride;

                    // Ambil nilai Y
                    int Y = yBuffer.get(yIndex) & 0xFF;

                    // UV coordinates (sub-sampling)
                    int uvx = x / 2;
                    int uvy = y / 2;
                    int uvIndex = uvy * uvRowStride + uvx * uvPixelStride;

                    int U = 128, V = 128; // Default neutral values

                    if (uvIndex < uBuffer.remaining() && uvIndex < vBuffer.remaining()) {
                        U = uBuffer.get(uvIndex) & 0xFF;
                        V = vBuffer.get(uvIndex) & 0xFF;
                    }

                    // YUV to RGB conversion
                    U -= 128;
                    V -= 128;

                    int R = Math.max(0, Math.min(255, (int)(Y + 1.402 * V)));
                    int G = Math.max(0, Math.min(255, (int)(Y - 0.344 * U - 0.714 * V)));
                    int B = Math.max(0, Math.min(255, (int)(Y + 1.772 * U)));

                    pixels[y * width + x] = (0xFF << 24) | (R << 16) | (G << 8) | B;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
            Log.d(TAG, "Successfully converted using RGB method: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "RGB conversion failed, trying grayscale fallback", e);
            return toBitmapGrayscale(image);
        }
    }

    // *** FALLBACK TERAKHIR: GRAYSCALE SEDERHANA ***
    private Bitmap toBitmapGrayscale(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
            ByteBuffer yBuffer = yPlane.getBuffer();

            int width = image.getWidth();
            int height = image.getHeight();
            int yRowStride = yPlane.getRowStride();
            int yPixelStride = yPlane.getPixelStride();

            int[] pixels = new int[width * height];

            // Hanya gunakan Y plane untuk grayscale
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * yRowStride + x * yPixelStride;
                    if (index < yBuffer.remaining()) {
                        int gray = yBuffer.get(index) & 0xFF;
                        pixels[y * width + x] = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;
                    }
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
            Log.d(TAG, "Successfully converted using grayscale fallback: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "All conversion methods failed", e);
            return null;
        }
    }

    private String buildSummaryString(List<RoboflowAPI.Prediction> predictions) {
        if (predictions == null || predictions.isEmpty()) {
            return "Tidak ada sampah terdeteksi.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== HASIL DETEKSI ===\n");
        sb.append("Jumlah objek: ").append(predictions.size()).append("\n\n");
        for (RoboflowAPI.Prediction p : predictions) {
            sb.append("• ").append(p.className)
                    .append(" (").append(String.format("%.1f", p.confidence * 100)).append("%)\n");
        }
        return sb.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}