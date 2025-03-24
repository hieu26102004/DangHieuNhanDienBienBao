import tensorflow as tf
from keras.models import model_from_json

# Load kiến trúc mô hình từ file JSON
with open('TSR_model.json', 'r') as json_file:
    model_json = json_file.read()

model = model_from_json(model_json)

# Load trọng số từ file .h5
model.load_weights('TSR.h5')

# Thêm layer Input vào để định nghĩa đầu vào rõ ràng
inputs = tf.keras.Input(shape=(30, 30, 3))
outputs = model(inputs)
model = tf.keras.Model(inputs=inputs, outputs=outputs)

# Chuyển sang TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()

# Lưu ra file .tflite
with open("TSR_converted.tflite", "wb") as f:
    f.write(tflite_model)

print("✅ Convert thành công!")