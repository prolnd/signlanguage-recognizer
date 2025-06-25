import os
import cv2
import numpy as np
import mediapipe as mp
import pandas as pd
from sklearn.preprocessing import StandardScaler
from sklearn.preprocessing import LabelEncoder
import matplotlib.pyplot as plt
from sklearn.svm import SVC
from sklearn.neural_network import MLPClassifier
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score
from sklearn.model_selection import train_test_split
import joblib
import time
from collections import Counter

# Initialize MediaPipe Hands
mp_hands = mp.solutions.hands
hands = mp_hands.Hands(static_image_mode=True, 
                      max_num_hands=1,
                      min_detection_confidence=0.5)


def extract_landmarks(image_path):

    image = cv2.imread(image_path)
    if image is None:
        print(f"Could not read image: {image_path}")
        return None
    
    # Convert image from BGR to RGB
    image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    
    # Process image with MediaPipe
    results = hands.process(image_rgb)
    
    # If no hand detected, return None
    if not results.multi_hand_landmarks:
        return None
    
    # Get landmarks of the first hand
    hand_landmarks = results.multi_hand_landmarks[0]
    
    # Extract x, y coordinates of each landmark (21 landmarks with x, y)
    landmarks = []
    h, w, _ = image.shape
    for landmark in hand_landmarks.landmark:
        # Normalize coordinates to be relative to image size
        x, y = landmark.x, landmark.y
        landmarks.extend([x, y])
    
    return landmarks

def process_dataset(base_dir):
    data = []
    labels = []
    
    for gesture_folder in os.listdir(base_dir):
        gesture_path = os.path.join(base_dir, gesture_folder)

        if os.path.isdir(gesture_path):

            # Process each image in the gesture folder
            for image_file in os.listdir(gesture_path):

                image_path = os.path.join(gesture_path, image_file)
                landmarks = extract_landmarks(image_path)
                
                if landmarks is not None:
                    data.append(landmarks)
                    labels.append(gesture_folder)
            
            print(f"Processed images for gesture: {gesture_folder}")
        
    # Create DataFrame
    landmark_names = []
    for i in range(21):
        landmark_names.extend([f'x{i}', f'y{i}'])
    
    df = pd.DataFrame(data, columns=landmark_names)
    df['gesture'] = labels
    
    return df

dataset = "images/asl_alphabet_train"
landmarks_df = process_dataset(dataset)
landmarks_df.to_csv("hand_landmarks_dataset.csv", index=False)
