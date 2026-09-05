package com.iykyk.videocollage.processing

import android.util.Log
import kotlin.math.sqrt

class ClusteringEngine(
    private val similarityThreshold: Float = DEFAULT_SIMILARITY_THRESHOLD
) {

    data class ClusteringResult(
        val clusters: List<PersonCluster>,
        val totalFaces: Int,
        val uniquePersons: Int
    )

    data class EmbeddingCluster(
        val id: Int,
        val centroid: FloatArray,
        val memberIndices: List<Int>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EmbeddingCluster) return false
            return id == other.id
        }
        override fun hashCode(): Int = id
    }

    fun clusterEmbeddings(embeddings: List<FloatArray>): List<EmbeddingCluster> {
        if (embeddings.isEmpty()) return emptyList()

        data class MutableCluster(
            val id: Int,
            val centroid: FloatArray,
            val memberIndices: MutableList<Int>
        )

        val clusters = mutableListOf<MutableCluster>()
        var nextId = 0

        for ((index, embedding) in embeddings.withIndex()) {
            var bestCluster: MutableCluster? = null
            var bestSimilarity = -1f

            for (cluster in clusters) {
                val similarity = SimilarityUtil.cosineSimilarity(embedding, cluster.centroid)
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestCluster = cluster
                }
            }

            if (bestCluster != null && bestSimilarity >= similarityThreshold) {
                bestCluster.memberIndices.add(index)
                val memberEmbeddings = bestCluster.memberIndices.map { embeddings[it] }
                val newCentroid = l2Normalize(averageEmbeddings(memberEmbeddings))
                newCentroid.copyInto(bestCluster.centroid)
            } else {
                clusters.add(
                    MutableCluster(
                        id = nextId++,
                        centroid = embedding.copyOf(),
                        memberIndices = mutableListOf(index)
                    )
                )
            }
        }

        return clusters.map { cluster ->
            EmbeddingCluster(
                id = cluster.id,
                centroid = cluster.centroid.copyOf(),
                memberIndices = cluster.memberIndices.toList()
            )
        }
    }

    fun cluster(facesWithEmbeddings: List<FaceWithEmbedding>): ClusteringResult {
        if (facesWithEmbeddings.isEmpty()) {
            return ClusteringResult(
                clusters = emptyList(),
                totalFaces = 0,
                uniquePersons = 0
            )
        }

        Log.d(TAG, "Clustering ${facesWithEmbeddings.size} faces with threshold=$similarityThreshold")

        val clusters = mutableListOf<PersonCluster>()
        var nextClusterId = 0

        val groupedByFrame = facesWithEmbeddings.withIndex().groupBy { it.value.face.timestampMs }
        val sortedTimestamps = groupedByFrame.keys.sorted()

        Log.d(TAG, "Frame-aware clustering: ${sortedTimestamps.size} unique timestamps, " +
                "${facesWithEmbeddings.size} total faces")

        for (timestamp in sortedTimestamps) {
            val frameFaces = groupedByFrame[timestamp]!!.map { it.value }
            val timeSec = timestamp / 1000.0

            if (frameFaces.size == 1) {
                val face = frameFaces[0]
                val embedding = face.embedding
                var bestCluster: PersonCluster? = null
                var bestSimilarity = -1f

                for (cluster in clusters) {
                    val similarity = SimilarityUtil.cosineSimilarity(embedding, cluster.centroid)
                    if (similarity > bestSimilarity) {
                        bestSimilarity = similarity
                        bestCluster = cluster
                    }
                }

                if (bestCluster != null && bestSimilarity >= similarityThreshold) {
                    Log.d(TAG, "Frame @ ${String.format("%.1f", timeSec)}s: 1 face -> MATCHED cluster ${bestCluster.id} (sim=${String.format("%.4f", bestSimilarity)})")
                    val updatedMembers = bestCluster.members + face
                    val newCentroid = computeCentroid(updatedMembers)
                    val updatedCluster = bestCluster.copy(
                        centroid = newCentroid,
                        members = updatedMembers,
                        appearanceCount = updatedMembers.size
                    )
                    clusters[clusters.indexOf(bestCluster)] = updatedCluster
                } else {
                    Log.d(TAG, "Frame @ ${String.format("%.1f", timeSec)}s: 1 face -> NEW cluster $nextClusterId (best sim=${String.format("%.4f", bestSimilarity)})")
                    val newCluster = PersonCluster.fromSingleFace(nextClusterId++, face)
                    clusters.add(newCluster)
                }
            } else {
                val assignedClusterIds = mutableSetOf<Int>()
                val faceClusterPairs = mutableListOf<Pair<FaceWithEmbedding, PersonCluster?>>()

                for (face in frameFaces) {
                    val embedding = face.embedding
                    var bestCluster: PersonCluster? = null
                    var bestSimilarity = -1f

                    for (cluster in clusters) {
                        if (cluster.id in assignedClusterIds) continue
                        val similarity = SimilarityUtil.cosineSimilarity(embedding, cluster.centroid)
                        if (similarity > bestSimilarity) {
                            bestSimilarity = similarity
                            bestCluster = cluster
                        }
                    }

                    if (bestCluster != null && bestSimilarity >= similarityThreshold) {
                        faceClusterPairs.add(face to bestCluster)
                        assignedClusterIds.add(bestCluster.id)
                        Log.d(TAG, "Frame @ ${String.format("%.1f", timeSec)}s face -> MATCHED cluster ${bestCluster.id} (sim=${String.format("%.4f", bestSimilarity)})")
                    } else {
                        faceClusterPairs.add(face to null)
                        Log.d(TAG, "Frame @ ${String.format("%.1f", timeSec)}s face -> unassigned (best sim=${String.format("%.4f", bestSimilarity)})")
                    }
                }

                for ((face, cluster) in faceClusterPairs) {
                    if (cluster != null) {
                        val updatedMembers = cluster.members + face
                        val newCentroid = computeCentroid(updatedMembers)
                        val updatedCluster = cluster.copy(
                            centroid = newCentroid,
                            members = updatedMembers,
                            appearanceCount = updatedMembers.size
                        )
                        clusters[clusters.indexOf(cluster)] = updatedCluster
                    } else {
                        val newCluster = PersonCluster.fromSingleFace(nextClusterId++, face)
                        clusters.add(newCluster)
                    }
                }
            }
        }

        Log.d(TAG, "Initial clustering: ${clusters.size} clusters")

        val mergedClusters = mergeSmallClusters(clusters)

        Log.d(TAG, "Final: ${mergedClusters.size} persons from ${facesWithEmbeddings.size} faces")
        for (cluster in mergedClusters) {
            val ts = cluster.members.map { it.face.timestampMs / 1000.0 }
            Log.d(TAG, "  Person ${cluster.id}: ${cluster.members.size} faces, timestamps=${ts.map { String.format("%.1f", it) }}")
        }

        return ClusteringResult(
            clusters = mergedClusters,
            totalFaces = facesWithEmbeddings.size,
            uniquePersons = mergedClusters.size
        )
    }

    private fun mergeSmallClusters(clusters: List<PersonCluster>): List<PersonCluster> {
        if (clusters.size <= 1) return clusters

        val sorted = clusters.sortedBy { it.members.size }.toMutableList()
        val result = clusters.toMutableList()

        for (smallCluster in sorted) {
            if (smallCluster.members.size > MAX_SMALL_CLUSTER_SIZE) continue
            if (smallCluster !in result) continue

            val smallTimestamps = smallCluster.members.map { it.face.timestampMs }.toSet()

            var bestTarget: PersonCluster? = null
            var bestSimilarity = -1f

            for (candidate in result) {
                if (candidate.id == smallCluster.id) continue
                if (candidate.members.size <= smallCluster.members.size) continue

                val candidateTimestamps = candidate.members.map { it.face.timestampMs }.toSet()
                if (smallTimestamps.intersect(candidateTimestamps).isNotEmpty()) continue

                val sim = SimilarityUtil.cosineSimilarity(smallCluster.centroid, candidate.centroid)
                if (sim > bestSimilarity) {
                    bestSimilarity = sim
                    bestTarget = candidate
                }
            }

            if (bestTarget != null && bestSimilarity >= MERGE_THRESHOLD) {
                val mergedMembers = bestTarget.members + smallCluster.members
                val newCentroid = computeCentroid(mergedMembers)
                val merged = bestTarget.copy(
                    centroid = newCentroid,
                    members = mergedMembers,
                    appearanceCount = mergedMembers.size
                )
                result.removeIf { it.id == smallCluster.id }
                result.removeIf { it.id == bestTarget.id }
                result.add(merged)
                Log.d(TAG, "  Post-merge: cluster ${smallCluster.id} (${smallCluster.members.size}) " +
                        "-> cluster ${bestTarget.id} (${bestTarget.members.size}) " +
                        "(sim=${String.format("%.4f", bestSimilarity)})")
            }
        }

        return result.sortedBy { it.id }.mapIndexed { idx, c -> c.copy(id = idx) }
    }

    private fun computeCentroid(members: List<FaceWithEmbedding>): FloatArray {
        val embeddings = members.map { it.embedding }
        return l2Normalize(averageEmbeddings(embeddings))
    }

    private fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
        val size = embeddings.first().size
        val result = FloatArray(size)

        for (embedding in embeddings) {
            for (i in 0 until size) {
                result[i] += embedding[i]
            }
        }

        val count = embeddings.size.toFloat()
        for (i in 0 until size) {
            result[i] /= count
        }

        return result
    }

    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var norm = 0f
        for (value in embedding) {
            norm += value * value
        }
        norm = sqrt(norm).coerceAtLeast(1e-12f)

        return FloatArray(embedding.size) { embedding[it] / norm }
    }

    companion object {
        private const val TAG = "ClusteringEngine"
        const val DEFAULT_SIMILARITY_THRESHOLD = 0.50f
        private const val MAX_SMALL_CLUSTER_SIZE = 2
        private const val MERGE_THRESHOLD = 0.35f
    }
}
