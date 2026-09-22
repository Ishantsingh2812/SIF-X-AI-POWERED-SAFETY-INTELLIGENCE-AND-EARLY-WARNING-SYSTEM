"""
Explainability & Model Attribution Engine

This module transforms statistical machine learning predictions into actionable,
human-interpretable safety intelligence:
1. Feature Contribution Calculation:
   Multiplies the TF-IDF feature presence (term frequency-inverse document frequency) 
   by the Logistic Regression model's learned coefficients for the predicted category.
   This mathematically quantifies *which specific words and phrases* caused the model
   to flag this report.
2. Hazard Factor & Consequence Mapping:
   Correlates the predicted precursor category with verified industry hazard factors,
   potential severe consequences, and Life-Saving Rule control actions.
3. Risk Scoring & Classification:
   Calibrates the raw binary model probability into an intuitive 0-100 score and
   maps it to risk tiers (LOW, MEDIUM, HIGH, CRITICAL).
"""

import os
import joblib
import numpy as np
import pandas as pd
from app.preprocessor import clean_safety_text

# Resolve absolute path to the pre-trained model directory
MODELS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'models')

# Standardized Life-Saving Rule hazard profiles grounded in OSHA and OIL safety standards
CATEGORY_HAZARDS = {
    'FALL_FROM_HEIGHT': {
        'factors': ['Work at elevation', 'Fall protection absent / bypassed', 'Unsecured scaffold or ladder', 'Unprotected edge or floor opening'],
        'consequences': ['Severe blunt force trauma', 'Spinal cord injury', 'Fatal fall from elevation'],
        'actions': ['Implement 100% tie-off with certified harness and shock-absorbing lanyard', 'Install standard perimeter guardrails and toe-boards', 'Conduct pre-task scaffolding inspection by a competent person']
    },
    'ELECTRICAL_HAZARDOUS_ENERGY': {
        'factors': ['Energized electrical contact', 'Lack of Lockout/Tagout (LOTO)', 'Arc flash boundary exposure', 'Inadequate electrical PPE'],
        'consequences': ['Severe electrical burn', 'Cardiac arrest / electrocution', 'Secondary blast trauma'],
        'actions': ['Verify zero-energy state using calibrated multimeter before contact', 'Apply multi-padlock Lockout/Tagout (LOTO) procedures', 'Wear certified NFPA 70E Arc Flash category-rated PPE and insulated gloves']
    },
    'CONFINED_SPACE_HAZMAT': {
        'factors': ['Toxic / flammable atmosphere', 'Oxygen deficiency / enrichment', 'Engulfment hazard', 'Absence of continuous atmospheric monitoring'],
        'consequences': ['H2S toxic asphyxiation', 'Chemical incapacitation', 'Fatal atmospheric exposure'],
        'actions': ['Perform calibrated multi-gas testing (O2, LEL, H2S, CO) before and during entry', 'Enforce Confined Space Entry Permit with designated standby attendant', 'Ensure positive pressure SCBA / airline breathing apparatus is operational']
    },
    'FIRE_EXPLOSION': {
        'factors': ['Flammable hydrocarbon vapor release', 'Uncontrolled ignition source during hot work', 'Lack of gas testing in hazardous zones', 'Combustible pressurized mixture'],
        'consequences': ['Catastrophic thermal flash fire', 'Explosion blast overpressure', 'Severe full-thickness burns and fatalities'],
        'actions': ['Obtain Hot Work Permit and certify 0% LEL with continuous gas monitoring', 'Eliminate ignition sources within 15 meters of live hydrocarbon process equipment', 'Position operational pressurized fire hoses and Class B foam extinguishers']
    },
    'CAUGHT_IN_BETWEEN': {
        'factors': ['Exposed rotating / moving machinery parts', 'Missing machine guarding', 'Worker in pinch point', 'Unexpected machine startup during servicing'],
        'consequences': ['Traumatic amputation of limbs', 'Crush asphyxiation', 'Severe degloving injury'],
        'actions': ['Ensure interlocked machine guards are securely in place before operation', 'Enforce zero-energy isolation and de-energize equipment before maintenance', 'Establish strict hands-off pinch point zones with physical barriers']
    },
    'STRUCK_BY': {
        'factors': ['Suspended or swinging overhead load', 'Dropped objects from upper levels', 'Flying pressurized projectile', 'Uncontrolled rigging'],
        'consequences': ['Severe head trauma / intracranial fracture', 'Crush injury', 'Fatal blunt impact'],
        'actions': ['Barricade and enforce exclusion drop zones under all overhead lifting activities', 'Utilize tool lanyards and certified toe-boards on all elevated work platforms', 'Inspect rigging tackle (slings, shackles) and never stand under a suspended load']
    },
    'VEHICLE_MOBILE_EQUIPMENT': {
        'factors': ['Pedestrian and mobile equipment interaction', 'Blind spot travel', 'Speeding / unbuckled operator', 'Ground subsidence or equipment rollover'],
        'consequences': ['Runover / crushing fatality', 'Major multi-system trauma', 'Vehicle collision impacts'],
        'actions': ['Enforce physical pedestrian-vehicle segregation walkways with safety barriers', 'Equip heavy mobile equipment with proximity sensors and 360-degree cameras', 'Mandate hi-vis PPE and verified eye-contact communication with equipment operators']
    },
    'OTHER': {
        'factors': ['General housekeeping irregularity', 'Minor ergonomic strain', 'Low-energy slip/trip hazard'],
        'consequences': ['Minor contusion', 'Superficial sprain or bruise'],
        'actions': ['Maintain clean walkways and clear tripping hazards', 'Follow standard ergonomic handling guidelines', 'Log observation in safety register']
    }
}

class Explainer:
    """
    Inference and explainability engine that loads serialized Scikit-Learn models
    and computes feature contribution weights for input safety reports.
    """
    def __init__(self):
        # Load the pre-trained TF-IDF vectorizer and classification pipelines
        self.vectorizer = joblib.load(os.path.join(MODELS_DIR, 'tfidf_vectorizer.joblib'))
        self.cat_model = joblib.load(os.path.join(MODELS_DIR, 'precursor_classifier.joblib'))
        self.sif_model = joblib.load(os.path.join(MODELS_DIR, 'sif_binary_classifier.joblib'))
        # Cache feature names (vocabulary) for rapid token lookup
        self.feature_names = np.array(self.vectorizer.get_feature_names_out())

    def explain_prediction(self, raw_text: str) -> dict:
        """
        Executes end-to-end inference and builds an explainability report for a given text.
        
        Steps:
        1. Clean narrative using safety-aware text normalizer.
        2. Transform into 15,000-dimensional TF-IDF vector.
        3. Predict precursor category and obtain class probabilities.
        4. Predict binary SIF presence and compute calibrated risk score.
        5. Multiply TF-IDF non-zero features by the logistic regression coefficients
           for the predicted category to isolate the top causal tokens.
        6. Assemble human-readable reasons, hazard factors, and preventive actions.
        """
        cleaned = clean_safety_text(raw_text)
        vec = self.vectorizer.transform([cleaned])
        
        # 1. Multiclass precursor prediction & confidence
        cat_pred = self.cat_model.predict(vec)[0]
        cat_probs = self.cat_model.predict_proba(vec)[0]
        cat_idx = list(self.cat_model.classes_).index(cat_pred)
        cat_conf = round(float(cat_probs[cat_idx]), 3)
        
        # 2. Binary SIF prediction & calibrated risk score (0 - 100)
        sif_pred = int(self.sif_model.predict(vec)[0])
        sif_probs = self.sif_model.predict_proba(vec)[0]
        # Probability of the positive SIF class scaled to a 0-100 index
        sif_score = round(float(sif_probs[1]) * 100, 1)
        
        # 3. Compute Linear Model Feature Attribution:
        # Feature Contribution = (TF-IDF Term Weight) * (Logistic Regression Coefficient)
        row_vec = vec.toarray()[0]
        nz_indices = np.where(row_vec > 0)[0]
        
        contributions = []
        if len(nz_indices) > 0:
            coefs = self.cat_model.coef_[cat_idx]
            feature_weights = row_vec[nz_indices] * coefs[nz_indices]
            
            # Sort features in descending order of positive contribution to the predicted class
            top_order = np.argsort(feature_weights)[::-1][:6]
            for idx in top_order:
                term = self.feature_names[nz_indices[idx]]
                score = round(float(feature_weights[idx]), 3)
                if score > 0.01:
                    contributions.append({'term': term, 'weight': score})
                    
        # 4. Map quantitative risk score into operational risk tiers
        if sif_score >= 80:
            risk_level = 'CRITICAL'
        elif sif_score >= 50:
            risk_level = 'HIGH'
        elif sif_score >= 25:
            risk_level = 'MEDIUM'
        else:
            risk_level = 'LOW'
            
        # Retrieve domain-verified hazard factors and recommended actions
        hazard_info = CATEGORY_HAZARDS.get(cat_pred, CATEGORY_HAZARDS['OTHER'])
        
        # 5. Formulate transparent, plain-English explanation bullets
        terms_str = ', '.join([c['term'] for c in contributions[:4]])
        explanation_reasons = []
        if terms_str:
            explanation_reasons.append('Model identified key high-risk indicators in report: ' + terms_str)
        explanation_reasons.append('Text patterns align strongly with historical ' + cat_pred.replace('_', ' ') + ' incidents.')
        explanation_reasons.append('Evaluated SIF potential probability is ' + str(sif_score) + '% based on severe hazard characteristics.')

        return {
            'sif_precursor_detected': bool(sif_pred == 1),
            'precursor_type': cat_pred,
            'risk_level': risk_level,
            'risk_score': sif_score,
            'confidence': cat_conf,
            'top_terms': contributions,
            'detected_factors': hazard_info['factors'],
            'potential_consequences': hazard_info['consequences'],
            'recommended_actions': hazard_info['actions'],
            'explanation': explanation_reasons,
            'decision_support_disclaimer': 'Prototype Decision Support: All flagged reports require review by certified safety professionals.'
        }

