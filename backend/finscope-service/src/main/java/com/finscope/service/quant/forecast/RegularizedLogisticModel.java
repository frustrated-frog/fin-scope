package com.finscope.service.quant.forecast;

import java.util.List;

final class RegularizedLogisticModel {
    private static final int ITERATIONS = 320;
    private static final double LEARNING_RATE = 0.12d;
    private static final double L2 = 0.02d;
    private final double[] means;
    private final double[] scales;
    private final double[] weights;

    private RegularizedLogisticModel(double[] means, double[] scales, double[] weights) {
        this.means = means;
        this.scales = scales;
        this.weights = weights;
    }

    static RegularizedLogisticModel fit(List<ForecastSample> samples) {
        if (samples == null || samples.isEmpty()) throw new IllegalArgumentException("训练样本不能为空");
        int dimensions = samples.get(0).getFeatures().length;
        double[] means = new double[dimensions];
        double[] scales = new double[dimensions];
        for (ForecastSample sample : samples) {
            double[] features = sample.getFeatures();
            if (features.length != dimensions) throw new IllegalArgumentException("预测特征维度不一致");
            for (int j = 0; j < dimensions; j++) means[j] += features[j];
        }
        for (int j = 0; j < dimensions; j++) means[j] /= samples.size();
        for (ForecastSample sample : samples) {
            double[] features = sample.getFeatures();
            for (int j = 0; j < dimensions; j++) {
                double delta = features[j] - means[j];
                scales[j] += delta * delta;
            }
        }
        for (int j = 0; j < dimensions; j++) {
            scales[j] = Math.sqrt(scales[j] / Math.max(1, samples.size() - 1));
            if (scales[j] < 1e-9d) scales[j] = 1d;
        }
        double[] weights = new double[dimensions + 1];
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            double[] gradient = new double[weights.length];
            for (ForecastSample sample : samples) {
                double[] normalized = normalize(sample.getFeatures(), means, scales);
                double error = sigmoid(score(weights, normalized)) - (sample.isPositive() ? 1d : 0d);
                gradient[0] += error;
                for (int j = 0; j < dimensions; j++) gradient[j + 1] += error * normalized[j];
            }
            double rate = LEARNING_RATE / Math.sqrt(1d + iteration / 40d);
            weights[0] -= rate * gradient[0] / samples.size();
            for (int j = 1; j < weights.length; j++) {
                weights[j] -= rate * (gradient[j] / samples.size() + L2 * weights[j]);
            }
        }
        return new RegularizedLogisticModel(means, scales, weights);
    }

    double predict(double[] features) {
        if (features.length != means.length) throw new IllegalArgumentException("预测特征维度不一致");
        return Math.max(0.01d, Math.min(0.99d, sigmoid(score(weights, normalize(features, means, scales)))));
    }

    private static double[] normalize(double[] features, double[] means, double[] scales) {
        double[] result = new double[features.length];
        for (int i = 0; i < features.length; i++) result[i] = (features[i] - means[i]) / scales[i];
        return result;
    }

    private static double score(double[] weights, double[] features) {
        double result = weights[0];
        for (int i = 0; i < features.length; i++) result += weights[i + 1] * features[i];
        return result;
    }

    private static double sigmoid(double value) {
        if (value > 30d) return 1d;
        if (value < -30d) return 0d;
        return 1d / (1d + Math.exp(-value));
    }
}
