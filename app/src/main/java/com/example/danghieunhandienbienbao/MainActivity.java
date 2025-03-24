package com.example.danghieunhandienbienbao;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
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

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.camera.view.PreviewView;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSION = 2;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private ArrayAdapter<String> arrayAdapter;
    private Interpreter tflite;
    private ExecutorService cameraExecutor;
    private Interpreter tfliteDirection, tfliteTraffic;
    private TextView txtDirection, txtTraffic;
    private List<String> directionLabels = Arrays.asList("TRÁI", "THẲNG", "PHẢI");
    PreviewView previewView;


    private final String[] trafficLabels = {
            "Giới hạn tốc độ (20km/h)",                        // 0
            "Giới hạn tốc độ (30km/h)",                        // 1
            "Giới hạn tốc độ (50km/h)",                        // 2
            "Giới hạn tốc độ (60km/h)",                        // 3
            "Giới hạn tốc độ (70km/h)",                        // 4
            "Giới hạn tốc độ (80km/h)",                        // 5
            "Hết giới hạn tốc độ (80km/h)",                    // 6
            "Giới hạn tốc độ (100km/h)",                       // 7
            "Giới hạn tốc độ (120km/h)",                       // 8
            "Cấm vượt",                                        // 9
            "Cấm vượt xe tải",                                 // 10
            "Ưu tiên đường chính",                             // 11
            "Đường ưu tiên",                                   // 12
            "Nhường đường",                                    // 13
            "Dừng lại",                                        // 14
            "Cấm phương tiện",                                 // 15
            "Cấm xe tải",                                      // 16
            "Cấm vào",                                         // 17
            "Nguy hiểm chung",                                 // 18
            "Khúc cua nguy hiểm bên trái",                    // 19
            "Khúc cua nguy hiểm bên phải",                    // 20
            "Stop",                                    // 21
            "Đường trơn trượt",                                // 22
            "Đường hẹp bên phải",                              // 23
            "Công trường",                                     // 24
            "Đèn tín hiệu giao thông",                         // 25
            "Người đi bộ",                                     // 26
            "Trẻ em băng qua đường",                           // 27
            "Xe đạp băng qua",                                 // 28
            "Cảnh báo băng tuyết/đá",                          // 29
            "Động vật hoang dã",                               // 30
            "Hết giới hạn cảnh báo",                           // 31
            "Phải đi thẳng",                                   // 32
            "Chỉ được rẽ phải",                                // 33
            "Đi thẳng",                               // 34
            "Rẽ trái",                           // 35
            "Đi thẳng hoặc rẽ phải",                           // 36
            "Đi bên phải vòng xuyến",                          // 37
            "Chỉ đường dành cho xe cơ giới",                   // 38
            "Hết lệnh cấm vượt",                               // 39
            "Hết lệnh cấm vượt xe tải",                        // 40
            "Giữ khoảng cách an toàn",                         // 41
            "Cấm quay đầu",                                    // 42
            "Cấm rẽ trái",                                     // 43
            "Cấm rẽ phải",                                     // 44
            "Hướng đi bắt buộc",                               // 45
            "Vạch sang đường",                                 // 46
            "Làn đường dành cho xe buýt",                      // 47
            "Cấm dừng đỗ",                                     // 48
            "Cấm còi",                                         // 49
            "Hết tất cả lệnh cấm",                             // 50
            "Cảnh báo tàu điện",                               // 51
            "Đường cụt",                                       // 52
            "Vượt xe máy",                                     // 53
            "Biển phụ",                                        // 54
            "Giao nhau với đường ưu tiên",                    // 55
            "Kết thúc mọi lệnh cấm",                           // 56
            "Biển báo cấm đi ngược chiều",                     // 57
            "Biển cảnh báo đường đôi bắt đầu",                 // 58
            "Biển cảnh báo đường đôi kết thúc",                // 59
            "Biển cảnh báo đường đôi bắt đầu",                 // 60 ❌ Trùng với 58
            "Biển cảnh báo đường đôi kết thúc"                 // 61 ❌ Trùng với 59
    };


    private TextView resultText;

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

        checkPermissions();
        tfliteDirection = loadModel("direction_model.tflite");
        tfliteTraffic = loadModel("traffic_model.tflite");


        txtDirection = findViewById(R.id.txtDirection);
        txtTraffic = findViewById(R.id.txtTraffic);


        startCamera();

        bluetoothListView.setOnItemClickListener((parent, view, position, id) -> {
            String address = arrayAdapter.getItem(position).split("\\n")[1];
            connectToDevice(address);
        });
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

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(ImageProxy imageProxy) {
        if (imageProxy == null || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        Bitmap bitmap = toBitmap(mediaImage);
        Bitmap grayscaleBitmap = toGrayscale(bitmap);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(grayscaleBitmap, 90, 90, true);
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);

        float[][] output = new float[1][3]; // 3 lớp
        tflite.run(inputBuffer, output);
        int predictedIndex = getMaxIndex(output[0]);
        String direction = directionLabels.get(predictedIndex);

        runOnUiThread(() -> {
            txtDirection.setText("Hướng: " + direction);
            sendDirectionViaBluetooth(direction);
        });

        imageProxy.close();
    }

    private Bitmap toBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        byte[] yBytes = new byte[yBuffer.remaining()];
        yBuffer.get(yBytes);
        YuvImage yuvImage = new YuvImage(yBytes, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
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

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(90 * 90 * 1);
        buffer.order(ByteOrder.nativeOrder());
        for (int y = 0; y < 90; y++) {
            for (int x = 0; x < 90; x++) {
                int pixel = bitmap.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                buffer.put((byte) r);
            }
        }
        buffer.rewind();
        return buffer;
    }

    private int getMaxIndex(float[] array) {
        int maxIndex = 0;
        float maxValue = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }
    private void sendDirectionViaBluetooth(String direction) {
        if (outputStream == null) return;

        String command = "";
        switch (direction) {
            case "trái": command = "L"; break;
            case "phải": command = "R"; break;
            case "thẳng": command = "F"; break;
        }

        try {
            outputStream.write(command.getBytes());
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
            if (tflite != null) tflite.close();
            cameraExecutor.shutdown();
        } catch (IOException e) {
            Log.e("Bluetooth", "Đóng kết nối thất bại", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Cần cấp quyền camera", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
