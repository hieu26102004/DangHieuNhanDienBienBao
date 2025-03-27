package com.example.danghieunhandienbienbao;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

public class DetectionResult {
    public final String label;
    public final float confidence;
    public final RectF box;

    public DetectionResult(String label, float confidence, RectF box) {
        this.label = label;
        this.confidence = confidence;
        this.box = box;
    }
    public List<DetectionResult> decodeOutput(float[][][] output, List<String> labels, float threshold) {
        List<DetectionResult> results = new ArrayList<>();

        int numBoxes = output[0][0].length; // 8400
        int numClasses = labels.size();     // 54

        for (int i = 0; i < numBoxes; i++) {
            float x = output[0][0][i];
            float y = output[0][1][i];
            float w = output[0][2][i];
            float h = output[0][3][i];
            float objectness = output[0][4][i];

            if (objectness < threshold) continue;

            // Tìm class có score cao nhất
            int maxClass = -1;
            float maxScore = -1;

            for (int j = 0; j < numClasses; j++) {
                float classScore = output[0][5 + j][i];
                if (classScore > maxScore) {
                    maxScore = classScore;
                    maxClass = j;
                }
            }

            float confidence = objectness * maxScore;
            if (confidence < threshold) continue;

            // Chuyển box từ [x_center, y_center, w, h] → [left, top, right, bottom]
            float left = x - w / 2;
            float top = y - h / 2;
            float right = x + w / 2;
            float bottom = y + h / 2;

            RectF rect = new RectF(left, top, right, bottom);
            results.add(new DetectionResult(labels.get(maxClass), confidence, rect));
        }

        return results;
    }

}

