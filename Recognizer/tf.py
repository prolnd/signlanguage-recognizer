import pandas as pd
import numpy as np
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder, StandardScaler
from sklearn.metrics import accuracy_score, classification_report
import json

def convert_to_trainable_format(df):
    """Convert DataFrame to training-ready format with proper preprocessing"""
    if df.isnull().values.any():
        
        print("Warning: Dataset contains missing values")
        df = df.dropna()
   
    label_encoder = LabelEncoder()
    y = label_encoder.fit_transform(df['gesture'])
   
    X = df.drop('gesture', axis=1).values
   
    # Normalize features
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)
   
    # Split into training and testing sets
    X_train, X_test, y_train, y_test = train_test_split(
        X_scaled, y, test_size=0.2, random_state=42, stratify=y
    )
   
    print(f"Training set size: {X_train.shape[0]}")
    print(f"Testing set size: {X_test.shape[0]}")
    print(f"Number of gestures: {len(label_encoder.classes_)}")
    print(f"Gestures: {label_encoder.classes_}")
    print(f"Number of features: {X_train.shape[1]}")
   
    return X_train, X_test, y_train, y_test, label_encoder, scaler

def create_lightweight_model(input_shape, num_classes):
    """Create a lightweight TensorFlow model optimized for mobile"""
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(input_shape,)),
        tf.keras.layers.Dense(128, activation='relu'),
        tf.keras.layers.Dropout(0.3),  
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.Dropout(0.2),  
        tf.keras.layers.Dense(num_classes, activation='softmax')
    ])
    
    return model

def train_lightweight_model(X_train, X_test, y_train, y_test, label_encoder):
    """Train the lightweight TensorFlow model"""
    input_shape = X_train.shape[1]
    num_classes = len(label_encoder.classes_)
    
    print("Creating lightweight model for mobile...")
    model = create_lightweight_model(input_shape, num_classes)
    
    print("Model architecture:")
    model.summary()
    
    # Compile model with mobile-optimized settings
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    
    # Callbacks for better training
    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor='val_accuracy',
            patience=15,
            restore_best_weights=True,
            verbose=1
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor='val_loss',
            factor=0.5,
            patience=8,
            min_lr=1e-6,
            verbose=1
        )
    ]
    
    print("\nTraining lightweight model...")
    
    # Train model
    history = model.fit(
        X_train, y_train,
        epochs=100,
        batch_size=32,
        validation_data=(X_test, y_test),
        callbacks=callbacks,
        verbose=1
    )
    
    # Evaluate
    test_loss, test_accuracy = model.evaluate(X_test, y_test, verbose=0)
    y_pred = model.predict(X_test, verbose=0)
    y_pred_classes = np.argmax(y_pred, axis=1)
    
    print(f"\nLightweight Model Results:")
    print(f"Test Accuracy: {test_accuracy:.4f}")
    print(f"Test Loss: {test_loss:.4f}")
    
    print("\nClassification Report:")
    print(classification_report(y_test, y_pred_classes, target_names=label_encoder.classes_))
    
    return model, test_accuracy, history

def convert_to_tflite(model, label_encoder, scaler):
    """Convert the TensorFlow model to TensorFlow Lite for mobile"""
    print("\nConverting to TensorFlow Lite for mobile...")
    
    # Convert to TensorFlow Lite 
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # Mobile-specific optimizations
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    
    # Add representative dataset for better optimization
    def representative_dataset():
        for i in range(100):
            # Generate representative data from your training set
            yield [np.random.random((1, model.input_shape[1])).astype(np.float32)]
    
    converter.representative_dataset = representative_dataset
    
    tflite_model = converter.convert()
    
    # Save the TFLite model
    tflite_filename = "gesture_model.tflite"
    with open(tflite_filename, 'wb') as f:
        f.write(tflite_model)
    
    print(f"TensorFlow Lite model saved as {tflite_filename}")
    print(f"Model size: {len(tflite_model) / 1024:.2f} KB")
    
    # Save labels for Android
    with open('labels.txt', 'w') as f:
        for label in label_encoder.classes_:
            f.write(f"{label}\n")
    
    # Save scaler parameters for Android
    scaler_params = {
        'mean': scaler.mean_.tolist(),
        'scale': scaler.scale_.tolist(),
        'var': scaler.var_.tolist(),
        'n_features': int(scaler.n_features_in_),
        'scaler_type': 'StandardScaler'
    }
    
    with open('scaler_params.json', 'w') as f:
        json.dump(scaler_params, f, indent=2)
    
    print(f"\nFiles created for Android:")
    print("- gesture_model.tflite")
    print("- labels.txt")
    print("- scaler_params.json")
    
    return tflite_model

def test_tflite_model(tflite_model, X_test, y_test, label_encoder):
    """Test the TensorFlow Lite model on full test set"""
    print("\nTesting TensorFlow Lite model...")
    
    # Load TFLite model
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    
    # Get input and output tensors
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print(f"Input shape: {input_details[0]['shape']}")
    print(f"Output shape: {output_details[0]['shape']}")
    
    # Test on full test set
    correct_predictions = 0
    total_samples = len(X_test)
    
    for i in range(total_samples):
        # Prepare input
        input_data = np.array([X_test[i]], dtype=np.float32)
        interpreter.set_tensor(input_details[0]['index'], input_data)
        
        # Run inference
        interpreter.invoke()
        
        # Get output
        output_data = interpreter.get_tensor(output_details[0]['index'])
        predicted_class = np.argmax(output_data[0])
        
        if predicted_class == y_test[i]:
            correct_predictions += 1
    
    tflite_accuracy = correct_predictions / total_samples
    print(f"TensorFlow Lite accuracy on full test set ({total_samples} samples): {tflite_accuracy:.4f}")
    
    return tflite_accuracy

def plot_training_history(history):
    """Plot training history for analysis"""
    try:
        import matplotlib.pyplot as plt
        
        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 4))
        
        # Plot accuracy
        ax1.plot(history.history['accuracy'], label='Training Accuracy')
        ax1.plot(history.history['val_accuracy'], label='Validation Accuracy')
        ax1.set_title('Model Accuracy')
        ax1.set_xlabel('Epoch')
        ax1.set_ylabel('Accuracy')
        ax1.legend()
        
        # Plot loss
        ax2.plot(history.history['loss'], label='Training Loss')
        ax2.plot(history.history['val_loss'], label='Validation Loss')
        ax2.set_title('Model Loss')
        ax2.set_xlabel('Epoch')
        ax2.set_ylabel('Loss')
        ax2.legend()
        
        plt.tight_layout()
        plt.savefig('training_history.png', dpi=150, bbox_inches='tight')
        plt.show()
        
    except ImportError:
        print("Matplotlib not available - skipping training plots")

def main(csv_path="hand_landmarks_dataset.csv"):
    """Main training pipeline for lightweight mobile model"""
    
    # Load data
    print(f"Loading data from {csv_path}...")
    
    try:
        df = pd.read_csv(csv_path)
        print(f"Dataset loaded: {df.shape}")
        print(f"Columns: {list(df.columns)}")
    except FileNotFoundError:
        print(f"Error: {csv_path} not found!")
        print("Make sure your dataset file exists in the current directory.")
        return False
    except Exception as e:
        print(f"Error loading dataset: {e}")
        return False
    
    # Prepare data
    try:
        X_train, X_test, y_train, y_test, label_encoder, scaler = convert_to_trainable_format(df)
    except Exception as e:
        print(f"Error preparing data: {e}")
        return False
    
    # Train lightweight model
    try:
        model, accuracy, history = train_lightweight_model(X_train, X_test, y_train, y_test, label_encoder)
    except Exception as e:
        print(f"Error training model: {e}")
        return False
    
    # Convert to TensorFlow Lite
    try:
        tflite_model = convert_to_tflite(model, label_encoder, scaler)
    except Exception as e:
        print(f"Error converting to TFLite: {e}")
        return False
    
    # Test TensorFlow Lite model
    try:
        tflite_accuracy = test_tflite_model(tflite_model, X_test, y_test, label_encoder)
    except Exception as e:
        print(f"Error testing TFLite model: {e}")
        tflite_accuracy = None
    
    # Plot training history
    plot_training_history(history)
    
    print("\n" + "=" * 60)
    print("Training completed successfully!")
    print(f"Final accuracy: {accuracy:.4f}")
    if tflite_accuracy:
        print(f"TensorFlow Lite accuracy: {tflite_accuracy:.4f}")
    print("Model files saved in current directory")
    print("Ready for Android deployment!")
    print("=" * 60)
    
    return True

if __name__ == "__main__":
    # Set random seeds for reproducibility
    tf.random.set_seed(42)
    np.random.seed(42)
    
    # Run training
    success = main(csv_path="hand_landmarks_dataset.csv")
    
    if not success:
        print("Training failed - check error messages above")
        exit(1)