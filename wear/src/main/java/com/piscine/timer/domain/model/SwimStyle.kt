package com.piscine.timer.domain.model

/**
 * Style de nage détecté par analyse des coups de bras.
 *
 * Heuristique sur montre :
 *   - CRAWL    : intervalles réguliers (~600–1500 ms), amplitude modérée
 *   - BRASSE   : intervalles irréguliers (phase de glisse longue), CoV élevé
 *   - PAPILLON : intervalles réguliers mais lents, amplitude forte
 *   - INCONNU  : moins de 4 coups enregistrés
 */
enum class SwimStyle(val label: String) {
    INCONNU("…"),
    CRAWL("Crawl"),
    BRASSE("Brasse"),
    PAPILLON("Papillon")
}
