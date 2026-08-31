package com.example.kycapp.biometrics.face

// Ports mod_face.py's LiveFaceVerifier match logic: both embeddings are
// already L2-normalized (FaceEmbedder.embed()), so their dot product is
// exactly the cosine similarity.
const val FACE_MATCH_THRESHOLD = 50.0

data class FaceMatchResult(val matched: Boolean, val confidencePercent: Double)

fun matchFaces(idEmbedding: FloatArray, liveEmbedding: FloatArray): FaceMatchResult {
    var dot = 0.0
    for (i in idEmbedding.indices) dot += idEmbedding[i].toDouble() * liveEmbedding[i].toDouble()
    val confidence = dot * 100.0
    return FaceMatchResult(matched = confidence > FACE_MATCH_THRESHOLD, confidencePercent = confidence)
}
