"""
Model Evaluation & Benchmarking Suite

This script evaluates the trained Scikit-Learn pipelines on the unseen, held-out test split:
1. Holdout Test Evaluation:
   Tests both models on 5,007 records that were NEVER exposed during training.
2. Multiclass Precursor Evaluation:
   Measures Accuracy, Precision, Recall, and F1-Score across all 8 hazard categories.
3. Binary SIF Precursor Detection Evaluation:
   Focuses intensely on RECALL for the positive SIF class.
   In safety-critical applications, a False Negative means failing to alert the team 
   about an impending fatality risk. Recall measures what fraction of genuine SIF precursors
   were successfully intercepted.
4. Confusion Matrix Generation:
   Reports True Positives, False Positives, True Negatives, and False Negatives.
5. Metrics Serialization:
   Saves full evaluation dictionary to `ai-service/models/evaluation_metrics.json`
   for transparent inspection by Hackathon judges and documentation tools.
"""

import os
import json
import joblib
import pandas as pd
import numpy as np
from sklearn.metrics import (
    classification_report, 
    confusion_matrix, 
    accuracy_score, 
    precision_recall_fscore_support
)

MODELS_DIR = 'ai-service/models'

def evaluate():
    """
    Loads saved test partition and models, computes comprehensive metrics,
    prints detailed classification tables, and exports metrics JSON.
    """
    print('Loading test dataset and models...')
    test_df = pd.read_csv(os.path.join(MODELS_DIR, 'test_dataset.csv'))
    vectorizer = joblib.load(os.path.join(MODELS_DIR, 'tfidf_vectorizer.joblib'))
    cat_model = joblib.load(os.path.join(MODELS_DIR, 'precursor_classifier.joblib'))
    sif_model = joblib.load(os.path.join(MODELS_DIR, 'sif_binary_classifier.joblib'))
    
    # Vectorize test narratives using the fitted vocabulary
    X_test_vec = vectorizer.transform(test_df['cleaned_narrative'])
    
    # --- 1. Evaluate Multiclass Precursor Categorization ---
    y_cat_true = test_df['precursor_category']
    y_cat_pred = cat_model.predict(X_test_vec)
    
    cat_accuracy = accuracy_score(y_cat_true, y_cat_pred)
    cat_report_dict = classification_report(y_cat_true, y_cat_pred, output_dict=True)
    cat_report_text = classification_report(y_cat_true, y_cat_pred)
    cat_cm = confusion_matrix(y_cat_true, y_cat_pred, labels=cat_model.classes_)
    
    print('=====================================================')
    print('=== MULTICLASS PRECURSOR CLASSIFICATION REPORT ===')
    print('=====================================================')
    print(f'Accuracy: {cat_accuracy:.4f}\n')
    print(cat_report_text)
    
    # --- 2. Evaluate Binary SIF Precursor Detection ---
    y_sif_true = test_df['is_sif']
    y_sif_pred = sif_model.predict(X_test_vec)
    
    sif_accuracy = accuracy_score(y_sif_true, y_sif_pred)
    sif_p, sif_r, sif_f1, _ = precision_recall_fscore_support(y_sif_true, y_sif_pred, average='binary')
    sif_report_dict = classification_report(y_sif_true, y_sif_pred, output_dict=True)
    sif_report_text = classification_report(y_sif_true, y_sif_pred)
    sif_cm = confusion_matrix(y_sif_true, y_sif_pred)
    
    print('=====================================================')
    print('=== BINARY SIF PRECURSOR DETECTION REPORT ===')
    print('=====================================================')
    print(f'SIF Class Accuracy: {sif_accuracy:.4f}')
    print(f'SIF Class Recall (Safety Critical): {sif_r:.4f}')
    print(f'SIF Class Precision: {sif_p:.4f}')
    print(f'SIF Class F1-Score: {sif_f1:.4f}\n')
    print(sif_report_text)
    print('Confusion Matrix (Binary SIF: [TN, FP], [FN, TP]):')
    print(sif_cm)
    # Highlight false negatives as the critical safety metric
    print(f'False Negatives (Critical to minimize): {sif_cm[1][0]} out of {sum(sif_cm[1])}')
    
    # Bundle metrics into JSON structure for frontend and backend consumers
    metrics = {
        'precursor_classification': {
            'accuracy': round(float(cat_accuracy), 4),
            'classes': cat_model.classes_.tolist(),
            'confusion_matrix': cat_cm.tolist(),
            'report': cat_report_dict
        },
        'sif_detection': {
            'accuracy': round(float(sif_accuracy), 4),
            'precision': round(float(sif_p), 4),
            'recall': round(float(sif_r), 4),
            'f1_score': round(float(sif_f1), 4),
            'confusion_matrix': sif_cm.tolist(),
            'report': sif_report_dict
        }
    }
    
    metrics_path = os.path.join(MODELS_DIR, 'evaluation_metrics.json')
    with open(metrics_path, 'w', encoding='utf-8') as f:
        json.dump(metrics, f, indent=2)
    print(f'\nSaved evaluation metrics to {metrics_path}')

if __name__ == '__main__':
    evaluate()

