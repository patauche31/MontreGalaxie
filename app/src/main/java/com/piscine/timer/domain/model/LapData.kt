package com.piscine.timer.domain.model

/**
 * Représente une longueur dans une session de nage.
 *
 * @param lapNumber   Numéro de la longueur (1, 2, 3, ...)
 * @param lapTimeMs   Durée de cette longueur en millisecondes
 * @param totalTimeMs Temps total écoulé depuis le début de la session
 */
data class LapData(
    val lapNumber: Int,
    val lapTimeMs: Long,
    val totalTimeMs: Long,
    /** Nombre de coups de bras sur cette longueur (0 si non mesuré) */
    val strokeCount: Int = 0
) {
    /** Distance cumulée — nécessite la longueur du bassin */
    fun distanceMeters(poolMeters: Int): Int = lapNumber * poolMeters

    /**
     * SWOLF = coups + secondes.
     * Indicateur d'efficacité : plus bas = meilleur (vitesse + économie d'énergie).
     * Retourne null si les coups n'ont pas été mesurés.
     */
    fun swolf(poolMeters: Int): Int? =
        if (strokeCount > 0) strokeCount + (lapTimeMs / 1000).toInt() else null

    /** Temps de cette longueur formaté mm:ss.d */
    val lapTimeFormatted: String get() = formatTime(lapTimeMs)

    /** Temps total formaté mm:ss.d */
    val totalTimeFormatted: String get() = formatTime(totalTimeMs)

    companion object {
        fun formatTime(ms: Long): String {
            val minutes = (ms / 60_000).toInt()
            val seconds = ((ms % 60_000) / 1_000).toInt()
            val tenths = ((ms % 1_000) / 100).toInt()
            return if (minutes > 0) {
                "%02d:%02d.%d".format(minutes, seconds, tenths)
            } else {
                "%02d.%d".format(seconds, tenths)
            }
        }
    }
}
