#!/usr/bin/env python3
"""
DeepFace Verification Server for Albanian ID Verification App
=============================================================

This Flask server receives two base64-encoded face images from the Android app
and returns whether they match using DeepFace with FaceNet512.

SETUP:
    pip install deepface flask flask-cors pillow

RUN:
    python deepface_server.py

    The server starts on 0.0.0.0:5005 (accessible from your phone on the same WiFi).
    Find your PC's local IP with:
        Windows:  ipconfig
        Mac/Linux: ifconfig  or  ip addr show

    Then set the server URL in the Android app to:  http://<YOUR_PC_IP>:5005

ENDPOINTS:
    GET  /health          → Check if server is running
    POST /verify          → Compare two faces
    POST /verify-file     → Compare two uploaded image files (for testing)

MODELS:
    Default: Facenet512 (highest accuracy, ~99.65% LFW)
    Alternatives: Facenet, ArcFace, SFace, VGG-Face, OpenFace, DeepFace, Dlib

    Change the model in the request body: { "model_name": "ArcFace" }
"""

import base64
import io
import os
import sys
import time
import traceback

from flask import Flask, request, jsonify

# Optional CORS support for browser-based testing
try:
    from flask_cors import CORS
    HAS_CORS = True
except ImportError:
    HAS_CORS = False

import numpy as np
from PIL import Image

# Lazy-load DeepFace to show a friendly error if it's missing
deepface = None

app = Flask(__name__)
if HAS_CORS:
    CORS(app)


def get_deepface():
    """Lazy-import DeepFace so startup errors are caught gracefully."""
    global deepface
    if deepface is None:
        try:
            from deepface import DeepFace
            deepface = DeepFace
            print("✓ DeepFace loaded successfully")
        except ImportError:
            print("✗ DeepFace not installed. Run: pip install deepface")
            sys.exit(1)
    return deepface


def base64_to_numpy(b64_string: str) -> np.ndarray:
    """Decode a base64 string into a NumPy image array (RGB)."""
    # Strip data URI prefix if present
    if "," in b64_string:
        b64_string = b64_string.split(",", 1)[1]

    img_bytes = base64.b64decode(b64_string)
    img = Image.open(io.BytesIO(img_bytes)).convert("RGB")
    return np.array(img)


# ---------- ROUTES ----------


@app.route("/health", methods=["GET"])
def health():
    """Simple health check endpoint."""
    return jsonify({
        "status": "ok",
        "service": "DeepFace Verification Server",
        "version": "1.0.0",
    })


@app.route("/verify", methods=["POST"])
def verify():
    """
    Compare two base64-encoded face images.

    Request JSON:
    {
        "img1": "<base64 JPEG/PNG>",
        "img2": "<base64 JPEG/PNG>",
        "model_name": "Facenet512",       # optional (default: Facenet512)
        "distance_metric": "cosine",      # optional (cosine | euclidean | euclidean_l2)
        "detector_backend": "opencv"      # optional (opencv | ssd | mtcnn | retinaface | skip)
    }

    Response JSON:
    {
        "verified": true/false,
        "distance": 0.1234,
        "threshold": 0.4000,
        "model": "Facenet512",
        "detector": "opencv",
        "time": 1.234
    }
    """
    start = time.time()
    DeepFace = get_deepface()

    try:
        data = request.get_json(force=True)

        img1_b64 = data.get("img1")
        img2_b64 = data.get("img2")
        model_name = data.get("model_name", "Facenet512")
        distance_metric = data.get("distance_metric", "cosine")
        detector_backend = data.get("detector_backend", "opencv")

        if not img1_b64 or not img2_b64:
            return jsonify({"error": "Both img1 and img2 are required"}), 400

        # Decode images
        img1 = base64_to_numpy(img1_b64)
        img2 = base64_to_numpy(img2_b64)

        print(f"  → Images decoded: {img1.shape}, {img2.shape}")
        print(f"  → Model: {model_name}, Metric: {distance_metric}, Detector: {detector_backend}")

        # Run verification
        result = DeepFace.verify(
            img1_path=img1,
            img2_path=img2,
            model_name=model_name,
            distance_metric=distance_metric,
            detector_backend=detector_backend,
            enforce_detection=False,  # Don't fail if face detection is weak
        )

        elapsed = time.time() - start

        response = {
            "verified": bool(result.get("verified", False)),
            "distance": float(result.get("distance", 1.0)),
            "threshold": float(result.get("threshold", 0.4)),
            "model": model_name,
            "detector": detector_backend,
            "time": round(elapsed, 3),
        }

        status = "✓ MATCH" if response["verified"] else "✗ NO MATCH"
        print(f"  → {status}  dist={response['distance']:.4f}  thr={response['threshold']:.4f}  ({elapsed:.2f}s)")

        return jsonify(response)

    except Exception as e:
        elapsed = time.time() - start
        traceback.print_exc()
        return jsonify({
            "error": str(e),
            "time": round(elapsed, 3),
        }), 500


@app.route("/verify-file", methods=["POST"])
def verify_file():
    """
    Compare two uploaded image files (for testing with curl or Postman).

    Usage:
        curl -X POST http://localhost:5005/verify-file \
             -F "img1=@id_photo.jpg" \
             -F "img2=@selfie.jpg" \
             -F "model_name=Facenet512"
    """
    start = time.time()
    DeepFace = get_deepface()

    try:
        if "img1" not in request.files or "img2" not in request.files:
            return jsonify({"error": "Both img1 and img2 files are required"}), 400

        model_name = request.form.get("model_name", "Facenet512")
        distance_metric = request.form.get("distance_metric", "cosine")

        img1 = np.array(Image.open(request.files["img1"].stream).convert("RGB"))
        img2 = np.array(Image.open(request.files["img2"].stream).convert("RGB"))

        result = DeepFace.verify(
            img1_path=img1,
            img2_path=img2,
            model_name=model_name,
            distance_metric=distance_metric,
            detector_backend="opencv",
            enforce_detection=False,
        )

        elapsed = time.time() - start

        return jsonify({
            "verified": bool(result.get("verified", False)),
            "distance": float(result.get("distance", 1.0)),
            "threshold": float(result.get("threshold", 0.4)),
            "model": model_name,
            "time": round(elapsed, 3),
        })

    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route("/models", methods=["GET"])
def list_models():
    """List all available DeepFace models."""
    return jsonify({
        "models": [
            {"name": "Facenet512", "accuracy": "99.65%", "embedding_dim": 512, "recommended": True},
            {"name": "Facenet", "accuracy": "99.63%", "embedding_dim": 128},
            {"name": "ArcFace", "accuracy": "99.53%", "embedding_dim": 512},
            {"name": "SFace", "accuracy": "99.60%", "embedding_dim": 128},
            {"name": "VGG-Face", "accuracy": "98.78%", "embedding_dim": 4096},
            {"name": "OpenFace", "accuracy": "93.80%", "embedding_dim": 128},
            {"name": "DeepFace", "accuracy": "97.35%", "embedding_dim": 4096},
            {"name": "Dlib", "accuracy": "99.38%", "embedding_dim": 128},
        ]
    })


# ---------- MAIN ----------


if __name__ == "__main__":
    print("=" * 60)
    print("  DeepFace Verification Server")
    print("  Albanian ID Verification Project")
    print("=" * 60)
    print()

    # Pre-load DeepFace + model on startup (so first request is faster)
    print("Loading DeepFace and Facenet512 model (first time may download ~100 MB)...")
    DeepFace = get_deepface()

    # Warm up the model with a dummy verification
    try:
        dummy = np.zeros((100, 100, 3), dtype=np.uint8)
        DeepFace.verify(
            img1_path=dummy,
            img2_path=dummy,
            model_name="Facenet512",
            detector_backend="skip",
            enforce_detection=False,
        )
        print("✓ Model pre-loaded and ready!")
    except Exception as e:
        print(f"⚠ Model warm-up had an issue (this is usually fine): {e}")

    print()
    print("Server starting on http://0.0.0.0:5005")
    print("Use your PC's local IP address in the Android app.")
    print()
    print("Quick test:  curl http://localhost:5005/health")
    print("=" * 60)

    app.run(host="0.0.0.0", port=5005, debug=False)
