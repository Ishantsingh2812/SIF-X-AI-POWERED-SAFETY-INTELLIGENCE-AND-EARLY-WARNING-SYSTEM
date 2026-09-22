"""
Safety Text Preprocessor & Precursor Taxonomy Derivation Module

This module is responsible for:
1. Normalizing raw incident narratives without losing critical safety signals.
   Crucially, words like "without", "no", "not", "unprotected", and "missing" are 
   strictly preserved because they completely invert the meaning of a safety report
   (e.g., "worker wearing harness" vs "worker without harness").
2. Deriving standardized Life-Saving Rule precursor categories from OSHA event titles
   and incident descriptions.
3. Classifying Serious Injury and Fatality (SIF) potential based on Campbell Institute
   and industrial safety standards.
"""

import re
import pandas as pd
import numpy as np

def clean_safety_text(text: str) -> str:
    """
    Cleans and normalizes free-form safety report text for NLP tokenization.
    
    Why this matters:
    - Removes carriage returns, newlines, and tabs that can distort tokenization.
    - Lowers case to ensure uniform feature extraction.
    - Preserves hyphenated safety terms (e.g. 'lock-out', 'struck-by', 'skid-steer')
      and numbers (e.g. '10 meters', '480v') which are vital severity indicators.
    - Avoids standard stopword removal that deletes critical negation words.
    
    Args:
        text: Raw safety report string.
        
    Returns:
        Cleaned, space-delimited string of tokens.
    """
    if not isinstance(text, str):
        return ''
    # Replace carriage returns, newlines, and tabs with single spaces
    text = text.replace(chr(13), ' ').replace(chr(10), ' ').replace(chr(9), ' ')
    text = text.lower()
    
    # Extract words and hyphenated compounds (e.g. 'struck-by', 'tie-off')
    tokens = re.findall(r'[a-z0-9]+(?:-[a-z0-9]+)?', text)
    return ' '.join(tokens)

def derive_precursor_category(event_title: str, narrative: str) -> str:
    """
    Maps an incident's standardized OSHA EventTitle and free-form narrative
    into one of eight recognized industrial Life-Saving Rule families.
    
    Precedence order:
    1. High-consequence specialized environments (Confined Space, Fire/Explosion, Electrical).
    2. Gravity / Elevation hazards (Fall from Height).
    3. Mobile Heavy Equipment (Forklifts, Trucks, Cranes).
    4. Mechanical hazards (Caught In / Between).
    5. Pressurized stored energy (Line of Fire).
    6. Kinetic impact (Struck By).
    7. Low-energy / general observations (Other).
    """
    et = str(event_title).lower() if event_title else ''
    nar = str(narrative).lower() if narrative else ''
    text = f'{et} {nar}'
    words = set(re.findall(r'[a-z0-9]+', text))
    
    # 1. Confined Space & Hazardous Atmosphere (H2S, oxygen deficiency, toxic gases)
    # Particularly crucial in upstream and midstream oil & gas extraction.
    if any(k in text for k in ['confined space', 'manhole', 'tank entry', 'trench', 'excavation', 'asphyxiat', 'h2s', 'hydrogen sulfide', 'toxic gas', 'oxygen deficient', 'suffocat', 'engulfment']):
        return 'CONFINED_SPACE_HAZMAT'
        
    # 2. Fire, Flash Fire & Explosion (Hydrocarbon ignition, gas leaks, welding sparks)
    if any(k in text for k in ['explosion', 'ignit', 'combust', 'blast', 'flammable', 'blowout', 'flare', 'flash fire', 'chemical burn', 'welding torch explosion']) or ('fire' in words and 'line of fire' not in text):
        return 'FIRE_EXPLOSION'
        
    # 3. Electrical & Hazardous Energy (Arc flash, energized lines, missing Lockout/Tagout - LOTO)
    if any(k in text for k in ['electric', 'arc flash', 'shock', 'electrocution', 'hazardous energy', 'energized', 'lockout', 'tagout', 'loto', 'power line', 'breaker', 'transformer', 'high voltage']):
        return 'ELECTRICAL_HAZARDOUS_ENERGY'

    # 4. Fall from Height (Ladders, scaffolding, elevated platforms, roofs)
    if any(k in text for k in ['fall to lower', 'fell from', 'fall from height', 'ladder', 'scaffold', 'roof', 'aerial lift', 'bucket truck', 'manlift', 'elevation']):
        return 'FALL_FROM_HEIGHT'

    # 5. Vehicle & Mobile Equipment (Forklifts, haul trucks, heavy machinery collisions)
    # Uses exact word matching to avoid substring collisions (e.g. 'truck' vs 'struck')
    vehicle_keywords = {'forklift', 'truck', 'trucks', 'vehicle', 'vehicles', 'tractor', 'skid-steer', 'excavator', 'loader', 'dozer', 'hauler'}
    if (words & vehicle_keywords) or 'powered vehicle' in text or 'pedestrian struck by vehicle' in text:
        return 'VEHICLE_MOBILE_EQUIPMENT'

    # 6. Caught In / Caught Between (Rotating machinery, pinch points, nip points, conveyors)
    if any(k in text for k in ['caught in', 'caught between', 'running equipment', 'pinch point', 'compressed or pinched', 'roller', 'conveyor', 'crushed between', 'auger', 'spindle']):
        return 'CAUGHT_IN_BETWEEN'

    # 7. Line of Fire (Pressurized hydraulic/air lines, whip checks, stored mechanical energy)
    if any(k in text for k in ['line of fire', 'pressurized hose', 'whip check', 'high pressure line', 'hydraulic release', 'stored energy release']):
        return 'LINE_OF_FIRE'

    # 8. Struck By (Dropped tools, flying particles, swinging crane loads)
    if 'struck' in words or any(k in text for k in ['falling object', 'flying object', 'dropped object', 'swinging load', 'hit by a falling', 'hit by a flying']):
        return 'STRUCK_BY'

    # Baseline category for low-energy slips, minor ergonomic observations, and housekeeping
    return 'OTHER'

# High-energy hazard categories that inherently carry fatal potential (Heinrich / Bird Safety Pyramid)
LIFE_THREATENING_PRECURSORS = {
    'FALL_FROM_HEIGHT', 'ELECTRICAL_HAZARDOUS_ENERGY', 
    'FIRE_EXPLOSION', 'CONFINED_SPACE_HAZMAT'
}

def is_sif_precursor(row: pd.Series, category: str) -> int:
    """
    Differentiates high-potential SIF precursors from standard recordable injuries.
    
    Methodology:
    - High-energy categories (Falls from elevation, Electrical contact, Explosions, Confined space)
      are unconditionally flagged as SIF precursors because the underlying energy release has fatal potential.
    - For other categories, severe anatomical trauma (skull fractures, internal organ damage, 
      amputations with bone loss, loss of consciousness) elevates the event to a SIF precursor.
    - Low-energy incidents (minor cuts, superficial sprains, finger pinches) return 0.
    
    Returns:
        1 if the incident represents a SIF precursor, otherwise 0.
    """
    if category in LIFE_THREATENING_PRECURSORS:
        return 1
        
    nature = str(row.get('NatureTitle', '')).lower()
    nar = str(row.get('Final Narrative', '')).lower()
    
    # Check for severe life-altering injury types
    if any(k in nature for k in ['electrocution', 'intracranial', 'internal injuries', 'concussion', 'amputation involving bone loss', 'crushing injuries']):
        return 1
    # Check for critical trauma indicators in the incident narrative
    if any(k in nar for k in ['unconscious', 'coma', 'fractured skull', 'crushed pelvis', 'multiple fractures', 'fall from roof', 'high voltage']):
        return 1
        
    return 0

