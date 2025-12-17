package com.predykt.accounting.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO contenant les features ML avancées (30+) pour matching bancaire
 *
 * Structure en 4 tiers :
 * - Tier 1 (10) : Features numériques avancées
 * - Tier 2 (8)  : Features NLP
 * - Tier 3 (6)  : Features temporelles
 * - Tier 4 (6)  : Features historiques
 *
 * @author PREDYKT ML Team
 * @version 2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvancedMatchFeatures {

    // ==================== TIER 1: FEATURES NUMÉRIQUES (10) ====================

    /**
     * Différence absolue de montant (XAF)
     */
    private Double amountDifference;

    /**
     * Ratio BT / GL (proche de 1.0 = montants similaires)
     */
    private Double amountRatio;

    /**
     * Différence en jours ouvrés (business days)
     */
    private Double dateDiffBusinessDays;

    /**
     * Différence en jours calendaires
     */
    private Double dateDiffCalendarDays;

    /**
     * log(BT) - log(GL) pour capturer variations relatives
     */
    private Double amountLogRatio;

    /**
     * Différence de percentile de montant (0-1)
     */
    private Double amountPercentileDiff;

    /**
     * Différence de jour du mois (1-31)
     */
    private Double dayOfMonthDiff;

    /**
     * Différence de semaine de l'année
     */
    private Double weekOfYearDiff;

    /**
     * Différence en mois
     */
    private Double monthDiff;

    /**
     * Différence de trimestre (Q1-Q4)
     */
    private Double quarterDiff;

    // ==================== TIER 2: FEATURES NLP (8) ====================

    /**
     * Similarité TF-IDF cosine (0-1)
     */
    private Double tfidfCosineSimilarity;

    /**
     * Distance de Levenshtein (plus bas = plus similaire)
     */
    private Double levenshteinDistance;

    /**
     * Similarité Jaro-Winkler (0-1, optimisé pour noms)
     */
    private Double jaroWinklerSimilarity;

    /**
     * Similarité Word2Vec (0-1) - nécessite modèle pré-entraîné
     */
    private Double word2vecSimilarity;

    /**
     * Chevauchement de bigrammes (2 caractères) (0-1)
     */
    private Double bigramOverlap;

    /**
     * Chevauchement de trigrammes (3 caractères) (0-1)
     */
    private Double trigramOverlap;

    /**
     * Score de correspondance floue (0-1)
     * Combinaison pondérée de Levenshtein + Jaro-Winkler
     */
    private Double fuzzyMatchScore;

    /**
     * Correspondance exacte du nom de l'entité (client/fournisseur)
     */
    private Double entityNameMatch;

    // ==================== TIER 3: FEATURES TEMPORELLES (6) ====================

    /**
     * Transaction pendant le weekend (1.0 ou 0.0)
     */
    private Double isWeekend;

    /**
     * Fin de mois (jour >= 28) (1.0 ou 0.0)
     */
    private Double isMonthEnd;

    /**
     * Début de mois (jour <= 3) (1.0 ou 0.0)
     */
    private Double isMonthStart;

    /**
     * Fin de trimestre (1.0 ou 0.0)
     */
    private Double isQuarterEnd;

    /**
     * Jour de la semaine (1=Lundi, 7=Dimanche)
     */
    private Double dayOfWeek;

    /**
     * Trimestre (1-4)
     */
    private Double quarter;

    // ==================== TIER 4: FEATURES HISTORIQUES (6) ====================

    /**
     * Taux de matching historique pour ce client (0-1)
     */
    private Double customerMatchRate;

    /**
     * Délai moyen de matching en jours
     */
    private Double avgMatchingDelay;

    /**
     * Fréquence d'utilisation du compte GL
     */
    private Double accountFrequency;

    /**
     * Fréquence de ce montant dans l'historique
     */
    private Double amountFrequency;

    /**
     * Fréquence de cette description
     */
    private Double descriptionFrequency;

    /**
     * Nombre de matches récents (30 derniers jours)
     */
    private Double recentMatchCount;

    // ==================== MÉTHODES ====================

    /**
     * Convertit en array pour ML (30 features)
     */
    public double[] toArray() {
        return new double[] {
            // Tier 1: Numériques (10)
            nvl(amountDifference),
            nvl(amountRatio),
            nvl(dateDiffBusinessDays),
            nvl(dateDiffCalendarDays),
            nvl(amountLogRatio),
            nvl(amountPercentileDiff),
            nvl(dayOfMonthDiff),
            nvl(weekOfYearDiff),
            nvl(monthDiff),
            nvl(quarterDiff),

            // Tier 2: NLP (8)
            nvl(tfidfCosineSimilarity),
            nvl(levenshteinDistance),
            nvl(jaroWinklerSimilarity),
            nvl(word2vecSimilarity),
            nvl(bigramOverlap),
            nvl(trigramOverlap),
            nvl(fuzzyMatchScore),
            nvl(entityNameMatch),

            // Tier 3: Temporelles (6)
            nvl(isWeekend),
            nvl(isMonthEnd),
            nvl(isMonthStart),
            nvl(isQuarterEnd),
            nvl(dayOfWeek),
            nvl(quarter),

            // Tier 4: Historiques (6)
            nvl(customerMatchRate),
            nvl(avgMatchingDelay),
            nvl(accountFrequency),
            nvl(amountFrequency),
            nvl(descriptionFrequency),
            nvl(recentMatchCount)
        };
    }

    /**
     * Noms des features (ordre DOIT correspondre à toArray())
     */
    public static String[] getFeatureNames() {
        return new String[] {
            // Tier 1: Numériques (10)
            "amount_difference",
            "amount_ratio",
            "date_diff_business_days",
            "date_diff_calendar_days",
            "amount_log_ratio",
            "amount_percentile_diff",
            "day_of_month_diff",
            "week_of_year_diff",
            "month_diff",
            "quarter_diff",

            // Tier 2: NLP (8)
            "tfidf_cosine_similarity",
            "levenshtein_distance",
            "jaro_winkler_similarity",
            "word2vec_similarity",
            "bigram_overlap",
            "trigram_overlap",
            "fuzzy_match_score",
            "entity_name_match",

            // Tier 3: Temporelles (6)
            "is_weekend",
            "is_month_end",
            "is_month_start",
            "is_quarter_end",
            "day_of_week",
            "quarter",

            // Tier 4: Historiques (6)
            "customer_match_rate",
            "avg_matching_delay",
            "account_frequency",
            "amount_frequency",
            "description_frequency",
            "recent_match_count"
        };
    }

    /**
     * Convertit en Map pour JSON storage
     */
    public Map<String, Object> toMap() {
        String[] names = getFeatureNames();
        double[] values = toArray();
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            map.put(names[i], values[i]);
        }
        return map;
    }

    /**
     * Construit depuis Map JSON
     */
    public static AdvancedMatchFeatures fromMap(Map<String, Object> map) {
        return AdvancedMatchFeatures.builder()
            // Tier 1
            .amountDifference(getDouble(map, "amount_difference"))
            .amountRatio(getDouble(map, "amount_ratio"))
            .dateDiffBusinessDays(getDouble(map, "date_diff_business_days"))
            .dateDiffCalendarDays(getDouble(map, "date_diff_calendar_days"))
            .amountLogRatio(getDouble(map, "amount_log_ratio"))
            .amountPercentileDiff(getDouble(map, "amount_percentile_diff"))
            .dayOfMonthDiff(getDouble(map, "day_of_month_diff"))
            .weekOfYearDiff(getDouble(map, "week_of_year_diff"))
            .monthDiff(getDouble(map, "month_diff"))
            .quarterDiff(getDouble(map, "quarter_diff"))

            // Tier 2
            .tfidfCosineSimilarity(getDouble(map, "tfidf_cosine_similarity"))
            .levenshteinDistance(getDouble(map, "levenshtein_distance"))
            .jaroWinklerSimilarity(getDouble(map, "jaro_winkler_similarity"))
            .word2vecSimilarity(getDouble(map, "word2vec_similarity"))
            .bigramOverlap(getDouble(map, "bigram_overlap"))
            .trigramOverlap(getDouble(map, "trigram_overlap"))
            .fuzzyMatchScore(getDouble(map, "fuzzy_match_score"))
            .entityNameMatch(getDouble(map, "entity_name_match"))

            // Tier 3
            .isWeekend(getDouble(map, "is_weekend"))
            .isMonthEnd(getDouble(map, "is_month_end"))
            .isMonthStart(getDouble(map, "is_month_start"))
            .isQuarterEnd(getDouble(map, "is_quarter_end"))
            .dayOfWeek(getDouble(map, "day_of_week"))
            .quarter(getDouble(map, "quarter"))

            // Tier 4
            .customerMatchRate(getDouble(map, "customer_match_rate"))
            .avgMatchingDelay(getDouble(map, "avg_matching_delay"))
            .accountFrequency(getDouble(map, "account_frequency"))
            .amountFrequency(getDouble(map, "amount_frequency"))
            .descriptionFrequency(getDouble(map, "description_frequency"))
            .recentMatchCount(getDouble(map, "recent_match_count"))

            .build();
    }

    // ==================== HELPERS ====================

    private double nvl(Double value) {
        return value != null ? value : 0.0;
    }

    private static Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    /**
     * Retourne le nombre total de features
     */
    public static int getFeatureCount() {
        return 30;  // 10 + 8 + 6 + 6
    }
}
