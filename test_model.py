import tensorflow as tf
import numpy as np
from PIL import Image

# Bản đồ nhãn
label_map = {
    0: '20_speed', 1: '30_speed', 2: '50_speed', 3: '60_speed', 4: '70_speed',
    5: '80_speed', 6: '80_lifted', 7: '100_speed', 8: '120_speed',
    9: 'no_overtaking_general', 10: 'no_overtaking_trucks',
    11: 'right_of_way_crossing', 12: 'right_of_way_general', 13: 'give_way',
    14: 'stop', 15: 'no_way_general', 16: 'no_way_trucks', 17: 'no_way_one_way',
    18: 'attention_general', 19: 'attention_left_turn', 20: 'attention_right_turn',
    21: 'attention_curvy', 22: 'attention_bumpers', 23: 'attention_slippery',
    24: 'attention_bottleneck', 25: 'attention_construction',
    26: 'attention_traffic_light', 27: 'attention_pedestrian',
    28: 'attention_children', 29: 'attention_bikes', 30: 'attention_snowflake',
    31: 'attention_deer', 32: 'lifted_general', 33: 'turn_right',
    34: 'turn_left', 35: 'turn_straight', 36: 'turn_straight_right',
    37: 'turn_straight_left', 38: 'turn_right_down', 39: 'turn_left_down',
    40: 'turn_circle', 41: 'lifted_no_overtaking_general',
    42: 'lifted_no_overtaking_trucks'
}

# Load mô hình TensorFlow Lite
interpreter = tf.lite.Interpreter(model_path='app/src/main/assets/gtsrb_model.lite')
interpreter.allocate_tensors()

# Lấy thông tin input/output
input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print("Thông tin Input:", input_details)
print("Thông tin Output:", output_details)

# Load và xử lý ảnh
image_path = 'test_image.jpg'  # Đảm bảo có ảnh ở cùng thư mục
image = Image.open(image_path).resize((224, 224))
input_data = np.array(image, dtype=np.float32) / 255.0  # Chuẩn hóa [0, 1]
input_data = np.expand_dims(input_data, axis=0)  # (1, 224, 224, 3)

# Dự đoán
interpreter.set_tensor(input_details[0]['index'], input_data)
interpreter.invoke()

# Lấy kết quả
output_data = interpreter.get_tensor(output_details[0]['index'])
predicted_class = np.argmax(output_data)

# Hiển thị kết quả
def print_prediction(output_data, label_map):
    print(f"🚦 Biển báo dự đoán: {label_map[predicted_class]} (Nhãn: {predicted_class})")
    print("🔢 Xác suất từng biển báo:")
    for idx, prob in enumerate(output_data[0]):
        print(f"{label_map[idx]}: {prob:.4f}")

print_prediction(output_data, label_map)