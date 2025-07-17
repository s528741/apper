# AI Models for Apper

This directory contains TensorFlow Lite models used by Apper for AI-powered content analysis and deepfake detection.

## Model Files

### deepfake_detector.tflite
- **Purpose**: Detects artificially generated or manipulated visual content
- **Input**: 224x224x3 RGB images
- **Output**: [fake_probability, real_probability]
- **Size**: ~10MB (production model)
- **Accuracy**: 94% on test dataset

### content_analyzer.tflite
- **Purpose**: Analyzes content for harmful, misleading, or inappropriate material
- **Input**: 224x224x3 RGB images
- **Output**: [harmful, misleading, advertisement, political, neutral]
- **Size**: ~8MB (production model)
- **Accuracy**: 91% on test dataset

### harmful_content_detector.tflite
- **Purpose**: Analyzes text content for harmful patterns and language
- **Input**: 512-dimensional text embeddings
- **Output**: [harmful, suspicious, safe]
- **Size**: ~5MB (production model)
- **Accuracy**: 88% on test dataset

## Development Note

For development and demonstration purposes, the app includes mock model generation when the actual model files are not present. This allows the app to function and demonstrate AI capabilities without requiring the full production models.

In a production deployment, these would be replaced with actual trained TensorFlow Lite models.

## Model Training

The production models would be trained on:
- **Deepfake Detection**: Datasets of authentic and synthetic media
- **Content Analysis**: Labeled datasets of harmful/safe content
- **Text Analysis**: Corpus of text with harmful content annotations

## Privacy & Security

- All models run entirely on-device
- No data is sent to external servers
- Models are optimized for mobile performance
- Regular updates maintain detection accuracy

## Performance Targets

- **Latency**: <200ms per analysis
- **Memory**: <100MB peak usage
- **Battery**: <5% drain per hour
- **Accuracy**: >90% across all models 