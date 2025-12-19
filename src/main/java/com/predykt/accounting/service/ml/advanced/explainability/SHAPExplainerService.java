package com.predykt.accounting.service.ml.advanced.explainability;

import com.predykt.accounting.dto.ml.AdvancedMatchFeatures;
import com.predykt.accounting.dto.ml.FeatureImportance;
import com.predykt.accounting.dto.ml.SHAPExplanation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service d'explicabilité ML avec SHAP (SHapley Additive exPlanations)
 *
 * SHAP quantifie la contribution de chaque feature à la prédiction finale.
 * Basé sur la théorie des jeux de Shapley.
 *
 * Implémentation:
 * - TreeSHAP pour XGBoost (via predictContrib)
 * - Calcul de l'impact de chaque feature en %
 * - Génération de rapports lisibles
 *
 * Références:
 * - Lundberg & Lee (2017): "A Unified Approach to Interpreting Model Predictions"
 * - XGBoost predict_contrib documentation
 *
 * @author PREDYKT ML Team
 * @version 2.0 - Full SHAP Implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SHAPExplainerService {

    /**
     * Calcule les SHAP values pour expliquer une prédiction
     *
     * Utilise XGBoost's predict with contrib=True pour obtenir les contributions
     * de chaque feature (approximation de TreeSHAP)
     *
     * @param booster Modèle XGBoost
     * @param features Features de la prédiction (30 features)
     * @param advancedFeatures Features avancées (pour noms et valeurs)
     * @param probability Probabilité prédite (0-1)
     * @param modelVersion Version du modèle
     * @return Explication SHAP complète
     */
    public SHAPExplanation explainPrediction(
            Booster booster,
            double[] features,
            AdvancedMatchFeatures advancedFeatures,
            double probability,
            String modelVersion
    ) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. Calculer les contributions via XGBoost
            float[] flatFeatures = toFloatArray(features);
            DMatrix dmatrix = new DMatrix(flatFeatures, 1, features.length);

            // XGBoost predict_contrib retourne [feature_contributions..., bias]
            // Le dernier élément est le bias (base_value)
            float[][] contribs = booster.predictContrib(dmatrix, 0);

            if (contribs == null || contribs.length == 0) {
                log.warn("XGBoost predictContrib returned null or empty");
                return createFallbackExplanation(features, advancedFeatures, probability, modelVersion);
            }

            float[] contributions = contribs[0];

            // 2. Extraire base value (dernier élément)
            double baseValue = contributions[contributions.length - 1];

            // 3. Extraire SHAP values (tous sauf le dernier)
            double[] shapValues = new double[features.length];
            for (int i = 0; i < features.length; i++) {
                shapValues[i] = contributions[i];
            }

            // 4. Créer les FeatureImportance objects
            List<FeatureImportance> allImportances = createFeatureImportances(
                    shapValues,
                    advancedFeatures
            );

            // 5. Trier par impact absolu et prendre le top 10
            List<FeatureImportance> topFeatures = allImportances.stream()
                    .sorted((a, b) -> Double.compare(
                            Math.abs(b.getShapValue()),
                            Math.abs(a.getShapValue())
                    ))
                    .limit(10)
                    .collect(Collectors.toList());

            // 6. Grouper par tier
            Map<String, List<FeatureImportance>> featuresByTier = allImportances.stream()
                    .collect(Collectors.groupingBy(FeatureImportance::getTier));

            // 7. Calculer la prédiction (devrait être proche de probability après sigmoid)
            double sumShap = Arrays.stream(shapValues).sum();
            double rawPrediction = baseValue + sumShap;

            long computationTime = System.currentTimeMillis() - startTime;

            SHAPExplanation explanation = SHAPExplanation.builder()
                    .shapValues(shapValues)
                    .topFeatures(topFeatures)
                    .baseValue(baseValue)
                    .prediction(rawPrediction)
                    .probability(probability)
                    .confidence(probability * 100.0)
                    .modelVersion(modelVersion)
                    .totalFeatures(features.length)
                    .featuresByTier(featuresByTier)
                    .computationTimeMs(computationTime)
                    .build();

            log.debug("SHAP explanation computed in {}ms - Top feature: {} (impact: {:.2f}%)",
                    computationTime,
                    topFeatures.isEmpty() ? "N/A" : topFeatures.get(0).getFeatureName(),
                    topFeatures.isEmpty() ? 0.0 : topFeatures.get(0).getImpactPercent());

            return explanation;

        } catch (XGBoostError e) {
            log.error("Erreur calcul SHAP values: {}", e.getMessage(), e);
            return createFallbackExplanation(features, advancedFeatures, probability, modelVersion);
        } catch (Exception e) {
            log.error("Erreur inattendue calcul SHAP: {}", e.getMessage(), e);
            return createFallbackExplanation(features, advancedFeatures, probability, modelVersion);
        }
    }

    /**
     * Crée les FeatureImportance objects à partir des SHAP values
     */
    private List<FeatureImportance> createFeatureImportances(
            double[] shapValues,
            AdvancedMatchFeatures advancedFeatures
    ) {
        List<FeatureImportance> importances = new ArrayList<>();

        // Noms des features (même ordre que toArray())
        String[] featureNames = AdvancedMatchFeatures.getFeatureNames();
        double[] featureValues = advancedFeatures.toArray();

        for (int i = 0; i < shapValues.length && i < featureNames.length; i++) {
            String featureName = featureNames[i];
            double shapValue = shapValues[i];
            double featureValue = featureValues[i];

            // Déterminer le tier
            String tier = determineTier(i);

            // Nom lisible et unité
            FeatureDisplayInfo displayInfo = getFeatureDisplayInfo(featureName, featureValue);

            FeatureImportance importance = FeatureImportance.builder()
                    .featureName(featureName)
                    .featureDisplayName(displayInfo.displayName)
                    .featureValue(featureValue)
                    .shapValue(shapValue)
                    .impactPercent(Math.abs(shapValue) * 100.0)
                    .direction(shapValue >= 0 ? "INCREASE" : "DECREASE")
                    .tier(tier)
                    .description(generateDescription(featureName, featureValue, shapValue))
                    .unit(displayInfo.unit)
                    .build();

            importances.add(importance);
        }

        return importances;
    }

    /**
     * Détermine le tier d'une feature (1-4) basé sur son index
     */
    private String determineTier(int index) {
        if (index < 10) return "TIER 1 - MONTANTS";
        else if (index < 18) return "TIER 2 - SIMILARITÉ TEXTUELLE";
        else if (index < 24) return "TIER 3 - TEMPORALITÉ";
        else return "TIER 4 - HISTORIQUE";
    }

    /**
     * Retourne les informations d'affichage pour une feature
     */
    private FeatureDisplayInfo getFeatureDisplayInfo(String featureName, double value) {
        return switch (featureName) {
            case "amount_difference" -> new FeatureDisplayInfo("Différence de montant", " XAF");
            case "amount_ratio" -> new FeatureDisplayInfo("Ratio montants", "");
            case "date_diff_business_days" -> new FeatureDisplayInfo("Jours ouvrés d'écart", " jours");
            case "date_diff_calendar_days" -> new FeatureDisplayInfo("Jours calendaires d'écart", " jours");
            case "amount_log_ratio" -> new FeatureDisplayInfo("Log ratio montants", "");
            case "amount_percentile_diff" -> new FeatureDisplayInfo("Différence percentile", "");
            case "day_of_month_diff" -> new FeatureDisplayInfo("Écart jour du mois", "");
            case "week_of_year_diff" -> new FeatureDisplayInfo("Écart semaine", "");
            case "month_diff" -> new FeatureDisplayInfo("Écart mois", "");
            case "quarter_diff" -> new FeatureDisplayInfo("Écart trimestre", "");

            case "tfidf_cosine_similarity" -> new FeatureDisplayInfo("TF-IDF Cosine", "%");
            case "levenshtein_distance" -> new FeatureDisplayInfo("Distance Levenshtein", "");
            case "jaro_winkler_similarity" -> new FeatureDisplayInfo("Jaro-Winkler", "%");
            case "word2vec_similarity" -> new FeatureDisplayInfo("Word2Vec", "%");
            case "bigram_overlap" -> new FeatureDisplayInfo("Bigram overlap", "%");
            case "trigram_overlap" -> new FeatureDisplayInfo("Trigram overlap", "%");
            case "fuzzy_match_score" -> new FeatureDisplayInfo("Fuzzy match", "%");
            case "entity_name_match" -> new FeatureDisplayInfo("Entity match", "%");

            case "is_weekend" -> new FeatureDisplayInfo("Weekend", "");
            case "is_month_end" -> new FeatureDisplayInfo("Fin de mois", "");
            case "is_month_start" -> new FeatureDisplayInfo("Début de mois", "");
            case "is_quarter_end" -> new FeatureDisplayInfo("Fin de trimestre", "");
            case "day_of_week" -> new FeatureDisplayInfo("Jour de la semaine", "");
            case "quarter" -> new FeatureDisplayInfo("Trimestre", "");

            case "customer_match_rate" -> new FeatureDisplayInfo("Taux match client", "%");
            case "avg_matching_delay" -> new FeatureDisplayInfo("Délai moyen match", " jours");
            case "account_frequency" -> new FeatureDisplayInfo("Fréquence compte", "");
            case "amount_frequency" -> new FeatureDisplayInfo("Fréquence montant", "");
            case "description_frequency" -> new FeatureDisplayInfo("Fréquence description", "");
            case "recent_match_count" -> new FeatureDisplayInfo("Matches récents", "");

            default -> new FeatureDisplayInfo(featureName, "");
        };
    }

    /**
     * Génère une description de l'impact
     */
    private String generateDescription(String featureName, double value, double shapValue) {
        String direction = shapValue > 0 ? "augmente" : "diminue";
        double impactPercent = Math.abs(shapValue) * 100.0;

        return String.format("%s la probabilité de %.1f%%", direction, impactPercent);
    }

    /**
     * Convertit double[] en float[]
     */
    private float[] toFloatArray(double[] doubles) {
        float[] floats = new float[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            floats[i] = (float) doubles[i];
        }
        return floats;
    }

    /**
     * Crée une explication fallback si SHAP échoue
     */
    private SHAPExplanation createFallbackExplanation(
            double[] features,
            AdvancedMatchFeatures advancedFeatures,
            double probability,
            String modelVersion
    ) {
        log.warn("Utilisation explication fallback (SHAP non disponible)");

        // Créer des importances factices basées sur des heuristiques
        double[] shapValues = new double[features.length];
        Arrays.fill(shapValues, 0.0);

        // Donner de l'importance aux features clés
        shapValues[0] = advancedFeatures.getAmountDifference() != null &&
                advancedFeatures.getAmountDifference() < 1000 ? 0.25 : 0.0;
        shapValues[10] = advancedFeatures.getTfidfCosineSimilarity() != null ?
                advancedFeatures.getTfidfCosineSimilarity() * 0.20 : 0.0;

        List<FeatureImportance> allImportances = createFeatureImportances(shapValues, advancedFeatures);
        List<FeatureImportance> topFeatures = allImportances.stream()
                .sorted((a, b) -> Double.compare(Math.abs(b.getShapValue()), Math.abs(a.getShapValue())))
                .limit(10)
                .collect(Collectors.toList());

        return SHAPExplanation.builder()
                .shapValues(shapValues)
                .topFeatures(topFeatures)
                .baseValue(0.0)
                .prediction(probability)
                .probability(probability)
                .confidence(probability * 100.0)
                .modelVersion(modelVersion + " (fallback)")
                .totalFeatures(features.length)
                .computationTimeMs(0L)
                .build();
    }

    /**
     * Classe interne pour stocker les infos d'affichage
     */
    private static class FeatureDisplayInfo {
        String displayName;
        String unit;

        FeatureDisplayInfo(String displayName, String unit) {
            this.displayName = displayName;
            this.unit = unit;
        }
    }
}
