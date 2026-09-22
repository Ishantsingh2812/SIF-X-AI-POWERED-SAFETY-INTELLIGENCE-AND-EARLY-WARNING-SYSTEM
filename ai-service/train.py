"""
Machine Learning Training Pipeline for SIF Precursor Detection

This script trains the core NLP and classification models:
1. Data Ingestion:
   Loads the prepared 25,000-record dataset of real incident narratives.
2. Stratified Train/Test Split (80/20):
   Ensures both training and test partitions retain identical proportions
   of all 8 precursor classes, preventing statistical sampling bias.
3. Feature Engineering via TF-IDF (Term Frequency - Inverse Document Frequency):
   - N-gram range (1, 2): Captures both unigrams ('harness', 'ladder') and bigrams 
     ('without harness', 'fall protection', 'lockout tagout').
   - Sublinear TF scaling: Applies logarithmic term frequency scaling (1 + log(tf)) 
     to prevent very frequent words from skewing predictions.
   - Max features (15,000): Caps vocabulary to the most informative tokens.
4. Multiclass Precursor Classifier:
   - LogisticRegression using L-BFGS solver with balanced class weighting.
5. Safety-Critical Binary SIF Classifier:
   - LogisticRegression with custom asymmetric class weights ({0: 1.0, 1: 1.8}).
   - Why this matters: In industrial safety, False Negatives (missing a life-threatening
     precursor) are catastrophic, whereas False Positives simply flag a report for safety review.
     Weighting the positive class heavily biases the optimization towards high recall (>91%).
"""

import os
import sys
import json
import joblib
import pandas as pd
import numpy as np

from sklearn.model_selection import train_test_split
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score

PROCESSED_DATA_PATH = 'ai-service/data/processed_safety_reports.csv'
MODELS_DIR = 'ai-service/models'

def train_models():
    """
    Executes the training workflow, serializes vectorizers and trained models
    using joblib, and stores holdout test records for reproducible evaluation.
    """
    os.makedirs(MODELS_DIR, exist_ok=True)
    print(f'Loading processed data from {PROCESSED_DATA_PATH}...')
    df = pd.read_csv(PROCESSED_DATA_PATH)
    
    # Filter out categories that do not have enough samples for meaningful statistical training
    class_counts = df['precursor_category'].value_counts()
    valid_classes = class_counts[class_counts >= 50].index.tolist()
    print(f'Valid precursor classes: {valid_classes}')
    df_filtered = df[df['precursor_category'].isin(valid_classes)].copy()
    
    X = df_filtered['cleaned_narrative']
    y_cat = df_filtered['precursor_category']
    y_sif = df_filtered['is_sif']
    
    # Step 1: Stratified 80/20 train/test split
    print('Performing stratified 80/20 train/test split...')
    X_train, X_test, y_cat_train, y_cat_test, y_sif_train, y_sif_test = train_test_split(
        X, y_cat, y_sif, test_size=0.2, random_state=42, stratify=y_cat
    )
    print(f'Training records: {len(X_train)}, Test records: {len(X_test)}')
    
    # Step 2: Fit TF-IDF Vectorizer ONLY on training partition to prevent data leakage
    print('Fitting TF-IDF Vectorizer with n-grams (1, 2)...')
    vectorizer = TfidfVectorizer(
        ngram_range=(1, 2),
        max_features=15000,
        sublinear_tf=True,
        min_df=2
    )
    X_train_vec = vectorizer.fit_transform(X_train)
    X_test_vec = vectorizer.transform(X_test)
    
    # Step 3: Multiclass Precursor Classifier
    print('Training Multiclass Precursor Classifier (lbfgs)...')
    cat_model = LogisticRegression(
        C=2.5,
        max_iter=1000,
        class_weight='balanced',
        solver='lbfgs',
        random_state=42
    )
    cat_model.fit(X_train_vec, y_cat_train)
    
    # Step 4: Binary SIF Precursor Detection Classifier (Optimizing Recall for Safety)
    print('Training Binary SIF Classifier (class_weight={0: 1.0, 1: 1.8})...')
    sif_model = LogisticRegression(
        C=2.0,
        max_iter=1000,
        class_weight={0: 1.0, 1: 1.8},
        solver='lbfgs',
        random_state=42
    )
    sif_model.fit(X_train_vec, y_sif_train)
    
    # Step 5: Serialize models for real-time inference
    joblib.dump(vectorizer, os.path.join(MODELS_DIR, 'tfidf_vectorizer.joblib'))
    joblib.dump(cat_model, os.path.join(MODELS_DIR, 'precursor_classifier.joblib'))
    joblib.dump(sif_model, os.path.join(MODELS_DIR, 'sif_binary_classifier.joblib'))
    print('Models saved successfully into ai-service/models!')
    
    # Step 6: Save holdout test split for strict, leak-free evaluation
    test_df = pd.DataFrame({
        'cleaned_narrative': X_test,
        'precursor_category': y_cat_test,
        'is_sif': y_sif_test
    })
    test_df.to_csv(os.path.join(MODELS_DIR, 'test_dataset.csv'), index=False)
    print('Test dataset saved for formal evaluation.')

if __name__ == '__main__':
    train_models()

