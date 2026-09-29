"""
FastAPI Microservice for AI/NLP Safety Report Inference

Role in Architecture:
- Acts as the dedicated AI inference worker.
- Receives JSON payloads containing safety incident narratives from Spring Boot.
- Invokes the pre-trained Scikit-Learn TF-IDF pipelines.
- Returns structured prediction data including SIF precursor classification,
  calibrated risk scores, active hazard factors, and interpretable word contributions.
"""

from fastapi import FastAPI, HTTPException, Security, Depends
from fastapi.security.api_key import APIKeyHeader
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
import os
import sys

# Ensure local imports work cleanly regardless of execution directory
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from app.explain import Explainer

# Initialize the FastAPI application instance
app = FastAPI(
    title='AI/NLP SIF Precursor Detection Engine',
    description='Machine learning inference service trained on 105,000+ real safety incident narratives to detect Serious Injury & Fatality (SIF) precursors with interpretable feature contributions.',
    version='1.0.0'
)

# API key security scheme
API_KEY_NAME = "X-API-Key"
api_key_header = APIKeyHeader(name=API_KEY_NAME, auto_error=False)
EXPECTED_API_KEY = os.environ.get("AI_SERVICE_API_KEY", "")

async def verify_api_key(api_key: Optional[str] = Security(api_key_header)):
    # If AI_SERVICE_API_KEY is configured, enforce strict verification
    if EXPECTED_API_KEY:
        if not api_key or api_key != EXPECTED_API_KEY:
            raise HTTPException(status_code=403, detail="Invalid or missing AI Service API Key")
    return api_key

# Configure Cross-Origin Resource Sharing (CORS)
cors_origins_env = os.environ.get("ALLOWED_ORIGINS", "*")
allowed_origins = [origin.strip() for origin in cors_origins_env.split(",") if origin.strip()]

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins if allowed_origins else ["*"],
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)

# Instantiate the Explainer once at startup to keep models in memory (avoids disk re-read per request)
explainer = None
try:
    explainer = Explainer()
except Exception as e:
    print(f"Warning: Explainer failed to load during startup: {e}")

# --- Pydantic Data Validation Schemas ---

class SafetyReportRequest(BaseModel):
    """Incoming request schema validated automatically by FastAPI."""
    report: str = Field(..., min_length=5, description='The textual description of the safety report/near miss/unsafe act')
    report_type: Optional[str] = Field(None, description='Unsafe Act, Unsafe Condition, or Near Miss')
    location: Optional[str] = Field(None, description='Facility, Rig, or Plant Location')

class FeatureContribution(BaseModel):
    """Schema representing an individual token's contribution to the prediction."""
    term: str = Field(..., description='Word or n-gram token extracted from the narrative')
    weight: float = Field(..., description='Mathematical contribution score (TF-IDF * Model Coef)')

class SIFPredictionResponse(BaseModel):
    """Structured response schema returned to Spring Boot / Frontend."""
    sif_precursor_detected: bool = Field(..., description='True if high SIF potential was detected')
    risk_level: str = Field(..., description='Categorical risk tier: LOW, MEDIUM, HIGH, or CRITICAL')
    risk_score: float = Field(..., description='Calibrated severity index from 0 to 100')
    precursor_type: str = Field(..., description='Standardized Life-Saving Rule category')
    confidence: float = Field(..., description='Model probability for the predicted category')
    detected_factors: List[str] = Field(..., description='Specific industrial hazard factors present')
    potential_consequences: List[str] = Field(..., description='Potential severe injuries / fatal outcomes')
    recommended_actions: List[str] = Field(..., description='Prescribed life-saving preventive controls')
    top_terms: List[FeatureContribution] = Field(..., description='Top salient words explaining the decision')
    explanation: List[str] = Field(..., description='Plain-English reasoning sentences')
    decision_support_disclaimer: str = Field(..., description='Legal and operational disclaimer for safety officers')

# --- API Endpoints ---

@app.get('/health')
def health_check():
    """
    Health check endpoint for container orchestrators and backend ping verification.
    """
    is_loaded = explainer is not None and getattr(explainer, 'cat_model', None) is not None
    return {
        'status': 'HEALTHY' if is_loaded else 'DEGRADED',
        'service': 'ai-nlp-sif-service',
        'model_loaded': is_loaded,
        'framework': 'scikit-learn + TF-IDF'
    }

@app.post('/predict', response_model=SIFPredictionResponse)
def predict_sif(request: SafetyReportRequest, _auth: Optional[str] = Depends(verify_api_key)):
    """
    Primary inference endpoint protected by optional shared secret key.
    
    Flow:
    1. Validates request body with Pydantic.
    2. Validates API key if configured.
    3. Runs text through the explainer pipeline.
    4. Returns structured prediction, attribution factors, and risk scores.
    """
    if explainer is None:
        raise HTTPException(status_code=503, detail="AI inference model is not loaded yet")
    try:
        result = explainer.explain_prediction(request.report)
        return result
    except Exception as e:
        # Wrap internal errors in HTTP 500 with meaningful diagnostic message
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == '__main__':
    # Enables running directly with `python main.py`
    import uvicorn
    uvicorn.run('main:app', host='0.0.0.0', port=8000, reload=False)

