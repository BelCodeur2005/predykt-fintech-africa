package com.predykt.accounting.dto.ml;

import com.predykt.accounting.domain.entity.BankTransaction;
import com.predykt.accounting.domain.entity.GeneralLedger;
import lombok.*;

import java.util.List;

/**
 * DTO représentant le résultat d'une prédiction ML
 *
 * @author PREDYKT ML Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionResult {

    /**
     * Transaction bancaire
     */
    private BankTransaction bankTransaction;

    /**
     * Écriture comptable
     */
    private GeneralLedger glEntry;

    /**
     * Score de confiance ML (0-100)
     */
    private Double confidenceScore;

    /**
     * Features utilisées pour la prédiction
     */
    private MatchFeatures features;

    /**
     * Features avancées (30 features) - Pour explication enrichie
     */
    private AdvancedMatchFeatures advancedFeatures;

    /**
     * Version du modèle ML utilisé
     */
    private String modelVersion;

    /**
     * Temps de calcul en millisecondes
     */
    private Long predictionTimeMs;

    /**
     * Explication de la prédiction (pour UI)
     */
    private String explanation;

    /**
     * Explication détaillée avec feature importance
     */
    private String detailedExplanation;

    /**
     * Top 5 features les plus importantes pour cette prédiction
     */
    private List<FeatureContribution> topFeatures;

    /**
     * Explication SHAP complète (30 features avec impact quantifié)
     */
    private SHAPExplanation shapExplanation;

    /**
     * ID du log de prédiction (pour traçabilité)
     */
    private Long predictionLogId;

    /**
     * Vérifie si la confiance est suffisante pour auto-validation
     */
    public boolean isHighConfidence() {
        return confidenceScore != null && confidenceScore >= 95.0;
    }

    /**
     * Génère une explication textuelle de la prédiction (SYSTÈME SHAP)
     *
     * Utilise les SHAP values calculées par XGBoost pour montrer l'impact RÉEL
     * et QUANTIFIÉ de chaque feature sur la prédiction.
     *
     * Format conforme au document ML_BANK_RECONCILIATION_ADVANCED.md (lignes 1428-1477)
     */
    public void generateExplanation() {
        // PRIORITÉ 1: Utiliser SHAP (système scientifique et précis)
        if (shapExplanation != null && !shapExplanation.getTopFeatures().isEmpty()) {
            generateSHAPExplanation();
        }
        // FALLBACK: Explication simple si SHAP non disponible
        else {
            generateFallbackExplanation();
        }
    }

    /**
     * Génère l'explication basée sur les SHAP values (PRÉFÉRÉ)
     */
    private void generateSHAPExplanation() {
        // Explication courte (top 5 features avec impact %)
        StringBuilder sb = new StringBuilder("🔍 EXPLICATION (SHAP):\n\n");
        sb.append("Top 5 facteurs ayant influencé cette prédiction:\n\n");

        int rank = 1;
        for (FeatureImportance feat : shapExplanation.getTopFeatures().subList(0, Math.min(5, shapExplanation.getTopFeatures().size()))) {
            String emoji = getEmojiForTier(feat.getTier());

            // 1. 💰 amount_difference = 0.00 XAF
            sb.append(String.format("%d. %s %s = %.2f%s\n",
                rank, emoji, feat.getFeatureDisplayName(),
                feat.getFeatureValue(), feat.getUnit()));

            //    ➜ Impact: +28.5% (montants quasi-identiques)
            String description = getFeatureImpactDescription(feat.getFeatureName(), feat.getFeatureValue());
            sb.append(String.format("   ➜ Impact: %s%.1f%% (%s)\n\n",
                feat.isPositiveImpact() ? "+" : "",
                feat.getImpactPercent(),
                description));

            rank++;
        }

        sb.append(String.format("🎯 Confiance: %.1f%% (modèle %s)",
            confidenceScore, modelVersion));

        this.explanation = sb.toString();

        // Explication détaillée (tous les tiers avec SHAP values)
        generateDetailedSHAPExplanation();
    }

    /**
     * Génère l'explication détaillée avec tous les tiers
     */
    private void generateDetailedSHAPExplanation() {
        StringBuilder sb = new StringBuilder("🔍 EXPLICATION DÉTAILLÉE (SHAP):\n\n");

        // Résumé des contributions
        sb.append(String.format("Prédiction finale: %.1f%%\n", shapExplanation.getProbability() * 100));
        sb.append(String.format("Base value: %.4f\n", shapExplanation.getBaseValue()));
        sb.append(String.format("Contribution positive: +%.4f\n", shapExplanation.getPositiveContribution()));
        sb.append(String.format("Contribution négative: %.4f\n\n", shapExplanation.getNegativeContribution()));

        // Top 10 features par impact
        sb.append("Top 10 features par impact absolu:\n\n");
        int rank = 1;
        for (FeatureImportance feat : shapExplanation.getTopFeatures().subList(0, Math.min(10, shapExplanation.getTopFeatures().size()))) {
            String emoji = getEmojiForTier(feat.getTier());
            sb.append(String.format("%d. %s %s\n", rank, emoji, feat.getFeatureDisplayName()));
            sb.append(String.format("   Valeur: %.2f%s\n", feat.getFeatureValue(), feat.getUnit()));
            sb.append(String.format("   Impact: %s%.1f%%\n",
                feat.isPositiveImpact() ? "+" : "", feat.getImpactPercent()));
            sb.append(String.format("   SHAP: %.4f (%s)\n\n",
                feat.getShapValue(), feat.getDirection()));
            rank++;
        }

        sb.append(String.format("⚙️ MODÈLE: %s (XGBoost avec TreeSHAP)\n", modelVersion));
        sb.append(String.format("⏱️ Temps calcul SHAP: %dms\n", shapExplanation.getComputationTimeMs()));

        this.detailedExplanation = sb.toString();
    }

    /**
     * Explication fallback simple si SHAP non disponible
     */
    private void generateFallbackExplanation() {
        StringBuilder sb = new StringBuilder("🤖 Prédiction ML:\n\n");

        if (advancedFeatures != null) {
            // Montrer quelques features clés (sans quantification d'impact)
            if (advancedFeatures.getAmountDifference() != null) {
                sb.append(String.format("💰 Différence montant: %.0f XAF\n",
                    advancedFeatures.getAmountDifference()));
            }
            if (advancedFeatures.getDateDiffCalendarDays() != null) {
                sb.append(String.format("📅 Écart dates: %.0f jours\n",
                    advancedFeatures.getDateDiffCalendarDays()));
            }
            if (advancedFeatures.getTfidfCosineSimilarity() != null) {
                sb.append(String.format("📝 Similarité texte: %.0f%%\n",
                    advancedFeatures.getTfidfCosineSimilarity() * 100));
            }
        }

        sb.append(String.format("\n🎯 Confiance: %.1f%% (modèle %s)\n",
            confidenceScore, modelVersion));
        sb.append("⚠️ Explication SHAP non disponible\n");

        this.explanation = sb.toString();
        this.detailedExplanation = explanation;
    }

    /**
     * Retourne l'emoji approprié pour un tier
     */
    private String getEmojiForTier(String tier) {
        if (tier == null) return "🔹";

        return switch (tier) {
            case "TIER 1 - MONTANTS" -> "💰";
            case "TIER 2 - SIMILARITÉ TEXTUELLE" -> "📝";
            case "TIER 3 - TEMPORALITÉ" -> "📅";
            case "TIER 4 - HISTORIQUE" -> "📊";
            default -> "🔹";
        };
    }

    /**
     * Génère une description textuelle de l'impact d'une feature
     */
    private String getFeatureImpactDescription(String featureName, double value) {
        return switch (featureName) {
            case "amount_difference" -> value < 100 ? "montants quasi-identiques" :
                    value < 1000 ? "montants très proches" : "différence de montants";

            case "tfidf_cosine_similarity" -> value > 0.9 ? "descriptions très similaires" :
                    value > 0.7 ? "descriptions similaires" : "descriptions partiellement similaires";

            case "date_diff_business_days", "date_diff_calendar_days" ->
                    value <= 1 ? "dates très proches" :
                            value <= 3 ? "dates proches" : "écart de dates notable";

            case "customer_match_rate" -> value > 0.9 ? "tiers avec excellent historique" :
                    value > 0.7 ? "tiers avec bon historique" : "tiers avec historique moyen";

            case "jaro_winkler_similarity" -> value > 0.9 ? "noms très similaires" :
                    value > 0.7 ? "noms similaires" : "noms partiellement similaires";

            case "recent_match_count" -> value > 5 ? "matches récents fréquents" :
                    value > 0 ? "matches récents détectés" : "pas de matches récents";

            case "is_weekend" -> value == 1.0 ? "transaction weekend" : "transaction semaine";

            case "is_month_end" -> value == 1.0 ? "transaction fin de mois" : "transaction milieu mois";

            default -> value > 0.5 ? "forte augmentation probabilité" : "forte diminution probabilité";
        };
    }

    /**
     * Classe interne pour représenter la contribution d'une feature (OBSOLÈTE - Utiliser FeatureImportance)
     * @deprecated Utiliser com.predykt.accounting.dto.ml.FeatureImportance à la place
     */
    @Deprecated
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureContribution {
        private String featureName;
        private String tier;
        private Double value;
        private Double importance;
        private String description;
    }
}