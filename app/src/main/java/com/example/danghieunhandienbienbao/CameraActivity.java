package com.example.danghieunhandienbienbao;
import android.Manifest;

import android.annotation.SuppressLint;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Matrix;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import android.util.Size;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity{
    private TextView resultTextView;
    private Interpreter tflite;
    private ExecutorService cameraExecutor;
    String[] trafficSignLabels = {
            "00forb_speed_over_20",
            "01forb_speed_over_30",
            "02forb_speed_over_50",
            "03forb_speed_over_60",
            "04forb_speed_over_70",
            "05forb_speed_over_80",
            "06forb_remove_speed_over_80",
            "07forb_speed_over_100",
            "08forb_speed_over_120",
            "09no_overtaking",
            "10no_overtaking_truck",
            "11priority_road_ahead",
            "12priority_road",
            "13yield",
            "14stop",
            "15no_vehicles",
            "16truck_mandatory",
            "17no_entry",
            "18danger_warning",
            "19left_curve",
            "20right_curve",
            "21dangerous_curves",
            "22uneven_road",
            "23slippery_road",
            "24right_narrowing",
            "25roadwork",
            "26traffic_light_ahead",
            "27pedestrian_crossing",
            "28school_zone",
            "29bicycle_crossing",
            "30snow_warning",
            "31wild_animal_crossing",
            "32no_stopping",
            "33no_right_turn",
            "34no_left_turn",
            "35no_straight_ahead",
            "36no_straight_or_right",
            "37no_straight_or_left",
            "38turn_down_right",
            "39turn_down_left",
            "40turn_roundabou",
            "41end_no_overtaking",
            "42end_no_overtaking_truck",
    };

    PreviewView previewView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera);

        previewView = findViewById(R.id.previewView);
        resultTextView = findViewById(R.id.resultTextView);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);

        try {
            Interpreter.Options options = new Interpreter.Options();
            tflite = new Interpreter(loadModelFile("model.tflite"), options);
        } catch (IOException e) {
            e.printStackTrace();
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();
    }
    private MappedByteBuffer loadModelFile(String modelName) throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 640))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    analyzeImage(image);
                    image.close();
                });


                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }
    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        Bitmap bitmap = ImageUtils.imageProxyToBitmap(imageProxy);
        Bitmap rotatedBitmap = rotateBitmap(bitmap, imageProxy.getImageInfo().getRotationDegrees());

        // Resize về 32x32
        Bitmap resized = Bitmap.createScaledBitmap(rotatedBitmap, 32, 32, true);

        // Chuyển về grayscale
        Bitmap grayscaleBitmap = toGrayscale(resized);

        // Chuyển Bitmap grayscale về float[][][][] [1, 32, 32, 1]
        float[][][][] input = new float[1][32][32][1];
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int pixel = grayscaleBitmap.getPixel(x, y);
                float r = Color.red(pixel);
                input[0][y][x][0] = r / 255.0f;  // Chuẩn hóa về 0-1
            }
        }

        // Output là [1, 3]
        float[][] output = new float[1][43];
        tflite.run(input, output);

        // Lấy index có xác suất cao nhất
        int predictedIndex = argmax(output[0]);
        String predictedLabel = trafficSignLabels[predictedIndex];

        runOnUiThread(() -> resultTextView.setText("Biển báo: " + predictedLabel));
    }
    private Bitmap toGrayscale(Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);  // chuyển thành grayscale
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }
    private int argmax(float[] array) {
        int maxIndex = 0;
        float max = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    public Set<String> decodeYoloLabelsOnly(float[][][] output, List<String> labelList, float confThreshold) {
        Set<String> resultLabels = new HashSet<>();  // Dùng Set để tránh trùng lặp
        int numBoxes = output[0][0].length;
        int numClasses = labelList.size();

        for (int i = 0; i < numBoxes; i++) {
            float objectness = output[0][4][i];
            if (objectness < confThreshold) continue;

            int bestClass = -1;
            float bestScore = 0;

            for (int j = 0; j < numClasses; j++) {
                float classProb = output[0][5 + j][i];
                if (classProb > bestScore) {
                    bestScore = classProb;
                    bestClass = j;
                }
            }

            float confidence = objectness * bestScore;
            if (confidence < confThreshold) continue;

            String label = labelList.get(bestClass);
            resultLabels.add(label);
        }

        return resultLabels;
    }



    private Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tflite != null) tflite.close();
        cameraExecutor.shutdown();
    }

    // 📡 Gửi label qua Bluetooth
//    private void sendLabelsToArduino(Set<String> labels) {
//        if (bluetoothSocket == null) return;
//
//        try {
//            OutputStream outputStream = bluetoothSocket.getOutputStream();
//            for (String label : labels) {
//                String message = label + "\n";
//                outputStream.write(message.getBytes());
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}
