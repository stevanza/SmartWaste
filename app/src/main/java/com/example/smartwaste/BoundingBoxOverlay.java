package com.example.smartwaste;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.smartwaste.api.RoboflowAPI;

import java.util.ArrayList;
import java.util.List;

public class BoundingBoxOverlay extends View {

    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();
    private List<RoboflowAPI.Prediction> predictions = new ArrayList<>();
    private int sourceImageWidth = 1;
    private int sourceImageHeight = 1;
    private int rotationDegrees = 0; // Tambahkan field untuk rotation

    public BoundingBoxOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8f);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setShadowLayer(5.0f, 4.0f, 4.0f, Color.BLACK);
    }

    // Update method untuk menerima rotation degrees
    public void setPredictions(List<RoboflowAPI.Prediction> predictions, int imageWidth, int imageHeight, int rotationDegrees) {
        this.predictions = (predictions != null) ? predictions : new ArrayList<>();
        this.sourceImageWidth = imageWidth;
        this.sourceImageHeight = imageHeight;
        this.rotationDegrees = rotationDegrees;
        invalidate();
    }

    // Overload method untuk backward compatibility
    public void setPredictions(List<RoboflowAPI.Prediction> predictions, int imageWidth, int imageHeight) {
        setPredictions(predictions, imageWidth, imageHeight, 0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (predictions == null || predictions.isEmpty()) {
            return;
        }

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        // Dimensi gambar asli (sebelum rotasi)
        float originalImageWidth = this.sourceImageWidth;
        float originalImageHeight = this.sourceImageHeight;

        // Dimensi gambar setelah rotasi
        float imageWidth, imageHeight;
        if (rotationDegrees == 90 || rotationDegrees == 270) {
            // Tukar width dan height untuk rotasi 90/270 derajat
            imageWidth = originalImageHeight;
            imageHeight = originalImageWidth;
        } else {
            imageWidth = originalImageWidth;
            imageHeight = originalImageHeight;
        }

        // Hitung skala dan offset untuk fit gambar ke view
        float scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);
        float offsetX = (viewWidth - (imageWidth * scale)) / 2f;
        float offsetY = (viewHeight - (imageHeight * scale)) / 2f;

        for (RoboflowAPI.Prediction prediction : predictions) {
            RectF originalBox = new RectF(prediction.boundingBox);
            RectF transformedBox = new RectF();

            // Transformasi koordinat berdasarkan rotasi
            switch (rotationDegrees) {
                case 0:
                    // Tidak ada rotasi
                    transformedBox.set(originalBox);
                    break;

                case 90:
                    // Rotasi 90 derajat searah jarum jam
                    transformedBox.left = originalImageHeight - originalBox.bottom;
                    transformedBox.top = originalBox.left;
                    transformedBox.right = originalImageHeight - originalBox.top;
                    transformedBox.bottom = originalBox.right;
                    break;

                case 180:
                    // Rotasi 180 derajat
                    transformedBox.left = originalImageWidth - originalBox.right;
                    transformedBox.top = originalImageHeight - originalBox.bottom;
                    transformedBox.right = originalImageWidth - originalBox.left;
                    transformedBox.bottom = originalImageHeight - originalBox.top;
                    break;

                case 270:
                    // Rotasi 270 derajat searah jarum jam (atau 90 derajat berlawanan)
                    transformedBox.left = originalBox.top;
                    transformedBox.top = originalImageWidth - originalBox.right;
                    transformedBox.right = originalBox.bottom;
                    transformedBox.bottom = originalImageWidth - originalBox.left;
                    break;

                default:
                    // Default ke tidak ada rotasi jika rotation tidak dikenal
                    transformedBox.set(originalBox);
                    break;
            }

            // Terapkan skala dan offset
            RectF finalBox = new RectF();
            finalBox.left = transformedBox.left * scale + offsetX;
            finalBox.top = transformedBox.top * scale + offsetY;
            finalBox.right = transformedBox.right * scale + offsetX;
            finalBox.bottom = transformedBox.bottom * scale + offsetY;

            // Gambar bounding box
            canvas.drawRect(finalBox, boxPaint);

            // Gambar label
            String label = prediction.className + ": " + String.format("%.1f", prediction.confidence * 100) + "%";
            canvas.drawText(label, finalBox.left, finalBox.top - 10, textPaint);
        }
    }
}