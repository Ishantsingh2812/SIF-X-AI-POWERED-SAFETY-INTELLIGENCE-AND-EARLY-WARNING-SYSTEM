"""
Dataset Extraction & Preparation Pipeline

This script prepares the raw OSHA Severe Incident & Injury dataset (105,996 records)
for machine learning model training:
1. Filters out records lacking descriptive narrative text.
2. Normalizes text using safety-aware text cleaning (retaining negation signals).
3. Derives the multiclass SIF Precursor taxonomy based on OSHA event classifications.
4. Derives the binary SIF Precursor ground truth based on Campbell Institute criteria.
5. Performs stratified downsampling (up to 3,500 samples per class) to prevent dominant
   classes from overwhelming minority high-hazard classes (e.g. electrical or gas ignition).
6. Exports the finalized clean dataset to `ai-service/data/processed_safety_reports.csv`.
"""

import os
import sys
import pandas as pd
import numpy as np

# Ensure helper modules from 'app' can be imported
sys.path.insert(0, os.path.abspath('ai-service'))
from app.preprocessor import clean_safety_text, derive_precursor_category, is_sif_precursor

# Paths for input source data and output processed data
DATASET_PATH = r'C:\Users\coder\OneDrive\Desktop\January2015toNovember2025\January2015toNovember2025.csv'
OUTPUT_PROCESSED_PATH = 'ai-service/data/processed_safety_reports.csv'

def prepare_dataset():
    """
    Reads the full CSV, cleans the text, extracts target labels,
    constructs a class-balanced sample, and writes to disk.
    """
    print(f'Loading raw dataset from {DATASET_PATH}...')
    df = pd.read_csv(DATASET_PATH, low_memory=False)
    print(f'Loaded {len(df)} records.')

    # Remove rows where Final Narrative is missing or whitespace-only
    df = df.dropna(subset=['Final Narrative']).copy()
    df = df[df['Final Narrative'].str.strip() != ''].copy()

    # Step 1: Clean and normalize incident descriptions
    print('Cleaning and normalizing safety report narratives...')
    df['cleaned_narrative'] = df['Final Narrative'].apply(clean_safety_text)
    # Remove extremely short descriptions (less than 3 words) that lack contextual value
    df = df[df['cleaned_narrative'].str.split().str.len() >= 3].copy()

    # Step 2: Derive standardized Life-Saving Rule precursor categories
    print('Deriving SIF precursor categories according to safety taxonomies...')
    df['precursor_category'] = df.apply(
        lambda r: derive_precursor_category(r.get('EventTitle', ''), r['Final Narrative']), 
        axis=1
    )

    # Step 3: Derive binary SIF precursor indicator (High Energy / Fatal Potential vs Recordable)
    print('Deriving SIF precursor binary flag...')
    df['is_sif'] = df.apply(lambda r: is_sif_precursor(r, r['precursor_category']), axis=1)

    # Specific metadata columns to retain in the processed dataset
    cols_to_keep = [
        'ID', 'EventDate', 'Employer', 'Primary NAICS', 
        'Hospitalized', 'Amputation', 'Loss of Eye',
        'Final Narrative', 'cleaned_narrative',
        'EventTitle', 'NatureTitle', 'Part of Body Title',
        'precursor_category', 'is_sif'
    ]
    
    # Step 4: Class-balanced stratified sampling
    # Why this matters: Severe events like Confined Space or Flash Fires occur less frequently
    # than general Struck-By incidents. Balancing prevents the model from ignoring rare hazards.
    balanced_dfs = []
    for cat, group in df.groupby('precursor_category'):
        n_samples = min(len(group), 3500)
        balanced_dfs.append(group.sample(n=n_samples, random_state=42))
    
    balanced_df = pd.concat(balanced_dfs).sample(frac=1.0, random_state=42).reset_index(drop=True)
    print(f'Constructed training subset with {len(balanced_df)} stratified records.')
    print('Category distribution in training set:')
    print(balanced_df['precursor_category'].value_counts())

    # Step 5: Save processed dataset
    balanced_df[cols_to_keep].to_csv(OUTPUT_PROCESSED_PATH, index=False)
    print(f'Saved processed dataset to {OUTPUT_PROCESSED_PATH}')

if __name__ == '__main__':
    prepare_dataset()

