package com.example.danghieunhandienbienbao;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.*;
import android.util.Log;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.camera.view.PreviewView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AutoCar";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSION = 2;
    private ExecutorService executorService;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private ArrayAdapter<String> arrayAdapter;
    private ExecutorService cameraExecutor;
    private Interpreter tfliteDirection, tfliteTraffic;
    private TextView txtDirection, txtTraffic;
    private List<String> directionLabels = Arrays.asList("TRÁI", "PHẢI", "THẲNG");
    String[] trafficSignLabels = {
            "00forb_speed_over_20",
            "01forb_speed_over_30",
            "02forb_speed_over_50",
            "03forb_speed_over_60",
            "04forb_speed_over_70",
            "05forb_speed_over_80",
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
    private String lastSentDirection = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView bluetoothListView = findViewById(R.id.bluetoothListView);
        previewView = findViewById(R.id.previewView);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        bluetoothListView.setAdapter(arrayAdapter);

        cameraExecutor = Executors.newSingleThreadExecutor();
        executorService = Executors.newFixedThreadPool(2);

        Interpreter.Options options = new Interpreter.Options();
        options.setUseXNNPACK(false); // Tắt XNNPACK
        try {
            tfliteTraffic = new Interpreter(loadModelFile("best_float32.tflite"), options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        checkPermissions();
        tfliteDirection = loadModel("direction_model.tflite");


        txtDirection = findViewById(R.id.txtDirection);
        txtTraffic = findViewById(R.id.txtTraffic);


        startCamera();

        bluetoothListView.setOnItemClickListener((parent, view, position, id) -> {
            String address = arrayAdapter.getItem(position).split("\\n")[1];
            connectToDevice(address);
        });
    }
    private MappedByteBuffer loadModelFile(String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.CAMERA
                }, REQUEST_BLUETOOTH_PERMISSION);
                return;
            }
        }
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
        } else {
            listPairedDevices();
        }
    }

    @SuppressLint("MissingPermission")
    private void listPairedDevices() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (!pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                arrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
            arrayAdapter.notifyDataSetChanged();
        }
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(String address) {
        try {
            if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                bluetoothSocket.close();
            }
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            Toast.makeText(this, "Kết nối thành công với: " + device.getName(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Kết nối thất bại", Toast.LENGTH_SHORT).show();
            Log.e("Bluetooth", "Error: ", e);
        }
    }

    private void sendCommand(String command) {
        try {
            if (outputStream != null) {
                outputStream.write(command.getBytes());
            }
        } catch (IOException e) {
            Log.e("Bluetooth", "Lỗi gửi lệnh", e);
        }
    }

    private Interpreter loadModel(String modelName) {
        try {
            AssetFileDescriptor fileDescriptor = getAssets().openFd(modelName);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            MappedByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            return new Interpreter(modelBuffer);
        } catch (IOException e) {
            Log.e("Model", "Load model thất bại: " + modelName, e);
            return null;
        }
    }
    private Interpreter loadTrafficSignModel(String modelName) {
        try {
            tfliteTraffic = new Interpreter(FileUtil.loadMappedFile(this, modelName));
            return tfliteTraffic;
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi tải model", Toast.LENGTH_SHORT).show();
            return null;
        }
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
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            } catch (Exception e) {
                Log.e("Camera", "Khởi động camera thất bại", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private float[][][][] bitmapToInput(Bitmap bitmap) {
        float[][][][] input = new float[1][640][640][3];

        for (int y = 0; y < 640; y++) {
            for (int x = 0; x < 640; x++) {
                int pixel = bitmap.getPixel(x, y);

                input[0][y][x][0] = ((pixel >> 16) & 0xFF) / 255.0f; // Red
                input[0][y][x][1] = ((pixel >> 8) & 0xFF) / 255.0f;  // Green
                input[0][y][x][2] = (pixel & 0xFF) / 255.0f;         // Blue
            }
        }

        return input;
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(ImageProxy imageProxy) {
        if (imageProxy == null || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        Bitmap bitmap = toBitmap(imageProxy);
        Bitmap grayscaleBitmap = toGrayscale(bitmap);
        // Tách ảnh thành 2 phần
        Bitmap topTwoThirds = Bitmap.createBitmap(grayscaleBitmap, 0, 0, grayscaleBitmap.getWidth(), 2 * grayscaleBitmap.getHeight() / 3);
        Bitmap bottomThird = cropBottomThird(grayscaleBitmap);

        executorService.execute(() -> processTrafficSign(topTwoThirds));
        executorService.execute(() -> processLineDetection(bottomThird));

        imageProxy.close();
    }

    private void processTrafficSign(Bitmap bitmap) {
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true);
        float[][][][] input = bitmapToInput(scaledBitmap);

        float[][][] output = new float[1][59][8400];
        tfliteTraffic.run(input, output);


        float confidenceThreshold = 0.5f;

        for (int i = 0; i < output[0][0].length; i++) {
            float confidence = output[0][4][i];
            if (confidence > confidenceThreshold) {
                float x = output[0][0][i];
                float y = output[0][1][i];
                float w = output[0][2][i];
                float h = output[0][3][i];
                int classId = (int) output[0][5][i];

                String signName = (classId >= 0 && classId < trafficSignLabels.length) ? trafficSignLabels[classId] : "unknown";
                Log.d("YOLO", "Biển báo: " + signName + " | Độ tin cậy: " + confidence);

                runOnUiThread(() -> {
                    txtTraffic.setText("Biển báo nhận diện: " + signName);
                    sendDirectionViaBluetooth(signName);
                });
            }
        }
    }


    private void processLineDetection(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 90, 90, true);
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);

        float[][] output = new float[1][3];
        tfliteDirection.run(inputBuffer, output);

        int predictedIndex = getMaxIndex(output[0]);
        String lineDirection = directionLabels.get(predictedIndex);
        Log.d("DEBUG", "directionLabels = " + lineDirection);

        runOnUiThread(() -> {
            txtDirection.setText("Line: " + lineDirection);
            sendDirectionViaBluetooth(lineDirection);
        });
    }


    private Bitmap toBitmap(ImageProxy image) {
        @SuppressLint("UnsafeOptInUsageError")
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] jpegBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }


    private Bitmap toGrayscale(Bitmap original) {
        Bitmap grayscale = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(original, 0, 0, paint);
        return grayscale;
    }
    private Bitmap cropBottomThird(Bitmap srcBmp) {
        int width = srcBmp.getWidth();
        int height = srcBmp.getHeight();
        int cropHeight = height / 3;

        // Cắt 1/3 dưới cùng của ảnh (từ vị trí 2/3 chiều cao)
        return Bitmap.createBitmap(srcBmp, 0, height - cropHeight, width, cropHeight);
    }


    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(90 * 90 * 4); // 4 bytes mỗi float
        buffer.order(ByteOrder.nativeOrder());
        for (int y = 0; y < 90; y++) {
            for (int x = 0; x < 90; x++) {
                int pixel = bitmap.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF; // vì là ảnh grayscale, chỉ cần 1 kênh
                float normalized = r / 255.0f;
                buffer.putFloat(normalized);
            }
        }
        buffer.rewind();
        return buffer;
    }

    private int getMaxIndex(float[] array) {
        int maxIndex = 0;
        float maxValue = array[0];
        for (int i = 0; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }
    private void sendDirectionViaBluetooth(String direction) {
        if (outputStream == null || direction.equals(lastSentDirection)) return;

        String command = "";
        switch (direction) {
            case "TRÁI": command = "L"; break;
            case "PHẢI": command = "R"; break;
            case "THẲNG": command = "F"; break;
        }

        try {
            outputStream.write(command.getBytes());
            lastSentDirection = direction; // Cập nhật hướng đã gửi
        } catch (IOException e) {
            Log.e("Bluetooth", "Gửi lệnh thất bại", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (outputStream != null) outputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
            if (tfliteTraffic != null) tfliteTraffic.close();
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
            }
        } catch (IOException e) {
            Log.e("Bluetooth", "Đóng kết nối thất bại", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                listPairedDevices();
            } else {
                Toast.makeText(this, "Cần cấp quyền Bluetooth và Camera để sử dụng ứng dụng", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
