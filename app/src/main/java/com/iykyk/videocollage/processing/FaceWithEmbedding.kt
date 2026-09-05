package com.iykyk.videocollage.processing

data class FaceWithEmbedding(
    val face: DetectedFace,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceWithEmbedding) return false
        return face == other.face && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = face.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
