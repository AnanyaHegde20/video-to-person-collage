package com.iykyk.videocollage.processing

data class PersonCluster(
    val id: Int,
    val centroid: FloatArray,
    val members: List<FaceWithEmbedding>,
    val appearanceCount: Int = members.size
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PersonCluster) return false
        return id == other.id
    }

    override fun hashCode(): Int = id

    companion object {
        fun fromSingleFace(id: Int, face: FaceWithEmbedding): PersonCluster {
            return PersonCluster(
                id = id,
                centroid = face.embedding.copyOf(),
                members = listOf(face),
                appearanceCount = 1
            )
        }
    }
}
