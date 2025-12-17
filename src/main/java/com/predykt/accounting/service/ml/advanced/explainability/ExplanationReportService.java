package com.predykt.accounting.service.ml.advanced.explainability;

import com.predykt.accounting.service.ml.advanced.explainability.FeatureImportanceService.FeatureImportance;
import com.predykt.accounting.service.ml.advanced.explainability.SHAPExplainerService.SHAPValues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Service de génération de rapports d'explication ML
 * Architecture: advanced/explainability/
 *
 * Génère des rapports lisibles pour:
 * - Utilisateurs métier (comptables, DAF)
 * - Data scientists (analyse détaillée)
 * - Auditeurs (traçabilité)
 *
 * Formats supportés:
 * - Texte (logs, console)
 * - HTML (rapports web)
 * - JSON (API, intégration)
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExplanationReportService {

    private final SHAPExplainerService shapService;
    private final FeatureImportanceService importanceService;

    /**
     * Génère un rapport d'explication pour une prédiction
     *
     * @param shapValues Valeurs SHAP de la prédiction
     * @param bankTransactionDesc Description de la transaction bancaire
     * @param generalLedgerDesc Description de l'écriture comptable
     * @return Rapport textuel lisible
     */
    public String generatePredictionReport(
            SHAPValues shapValues,
            String bankTransactionDesc,
            String generalLedgerDesc) {

        StringBuilder sb = new StringBuilder();

        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║     RAPPORT D'EXPLICATION - MATCHING BANCAIRE ML             ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        // Informations générales
        sb.append("📅 Date: ").append(getCurrentTimestamp()).append("\n\n");

        // Candidats du match
        sb.append("🏦 TRANSACTION BANCAIRE:\n");
        sb.append("   ").append(truncate(bankTransactionDesc, 70)).append("\n\n");

        sb.append("📒 ÉCRITURE COMPTABLE:\n");
        sb.append("   ").append(truncate(generalLedgerDesc, 70)).append("\n\n");

        // Prédiction
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("🎯 PRÉDICTION DU MODÈLE\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");

        double probability = shapValues.getPrediction();
        String decision = probability >= 0.5 ? "MATCH ✅" : "NO MATCH ❌";
        String confidence = getConfidenceLevel(probability);

        sb.append(String.format("Décision:     %s\n", decision));
        sb.append(String.format("Probabilité:  %.2f%%\n", probability * 100));
        sb.append(String.format("Confiance:    %s\n\n", confidence));

        // Explication SHAP
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("🔍 EXPLICATION DE LA PRÉDICTION (SHAP)\n");
        sb.append("═══════════════════════════════════════════════════════════════\n\n");

        sb.append(String.format("Baseline (moyenne): %.2f%%\n",
                                shapValues.getBaseValue() * 100));
        sb.append(String.format("Impact total:       %.2f%%\n\n",
                                (probability - shapValues.getBaseValue()) * 100));

        // Facteurs positifs
        sb.append("🔼 FACTEURS FAVORISANT LE MATCH:\n");
        if (shapValues.getTopPositive().isEmpty()) {
            sb.append("   Aucun facteur positif significatif\n\n");
        } else {
            shapValues.getTopPositive().forEach(c -> {
                String featureLabel = getFeatureLabel(c.getFeatureName());
                String impact = formatImpact(c.getContribution());
                sb.append(String.format("   • %s: %s\n", featureLabel, impact));
            });
            sb.append("\n");
        }

        // Facteurs négatifs
        sb.append("🔽 FACTEURS DÉFAVORISANT LE MATCH:\n");
        if (shapValues.getTopNegative().isEmpty()) {
            sb.append("   Aucun facteur négatif significatif\n\n");
        } else {
            shapValues.getTopNegative().forEach(c -> {
                String featureLabel = getFeatureLabel(c.getFeatureName());
                String impact = formatImpact(c.getContribution());
                sb.append(String.format("   • %s: %s\n", featureLabel, impact));
            });
            sb.append("\n");
        }

        // Recommandation
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("💡 RECOMMANDATION\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append(generateRecommendation(probability)).append("\n\n");

        sb.append("╚══════════════════════════════════════════════════════════════╝\n");

        return sb.toString();
    }

    /**
     * Génère un rapport d'importance globale des features
     */
    public String generateFeatureImportanceReport(FeatureImportance importance) {
        StringBuilder sb = new StringBuilder();

        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║     IMPORTANCE DES FEATURES - MODÈLE ML                      ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        sb.append("📅 Date: ").append(getCurrentTimestamp()).append("\n");
        sb.append("🤖 Modèle: ").append(importance.getModelType()).append("\n");
        sb.append("📊 Type: ").append(importance.getImportanceType()).append("\n\n");

        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("🏆 TOP 15 FEATURES LES PLUS IMPORTANTES\n");
        sb.append("═══════════════════════════════════════════════════════════════\n\n");

        sb.append(String.format("%-4s %-40s %-10s %s\n",
                                "Rang", "Feature", "Score", "Bar"));
        sb.append("-".repeat(70) + "\n");

        int rank = 1;
        for (var fs : importance.getRankedFeatures().subList(0,
                Math.min(15, importance.getRankedFeatures().size()))) {

            String featureLabel = getFeatureLabel(fs.getFeatureName());
            String scoreStr = String.format("%.4f", fs.getScore());
            String bar = generateBar(fs.getScore(), 20);

            sb.append(String.format("%-4d %-40s %-10s %s\n",
                                    rank++, featureLabel, scoreStr, bar));
        }

        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("📌 INTERPRÉTATION\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append(generateFeatureInterpretation(importance)).append("\n");

        sb.append("╚══════════════════════════════════════════════════════════════╝\n");

        return sb.toString();
    }

    /**
     * Obtient le timestamp actuel formaté
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        );
    }

    /**
     * Obtient un label lisible pour une feature
     */
    private String getFeatureLabel(String featureName) {
        Map<String, String> labels = Map.ofEntries(
            Map.entry("amount_difference", "Différence de montant"),
            Map.entry("amount_ratio", "Ratio de montant"),
            Map.entry("date_diff_business_days", "Écart de dates (jours ouvrés)"),
            Map.entry("tfidf_cosine_similarity", "Similarité textuelle (TF-IDF)"),
            Map.entry("levenshtein_distance", "Distance Levenshtein"),
            Map.entry("jaro_winkler_similarity", "Similarité Jaro-Winkler"),
            Map.entry("customer_match_rate", "Taux de match historique"),
            Map.entry("account_frequency", "Fréquence du compte"),
            Map.entry("amount_frequency", "Fréquence du montant"),
            Map.entry("is_weekend", "Est un weekend"),
            Map.entry("is_month_end", "Est fin de mois")
        );

        return labels.getOrDefault(featureName, featureName);
    }

    /**
     * Formate l'impact SHAP en texte lisible
     */
    private String formatImpact(double contribution) {
        double absContribution = Math.abs(contribution);
        String sign = contribution > 0 ? "+" : "-";

        if (absContribution > 0.1) {
            return String.format("%s%.3f (Impact FORT)", sign, absContribution);
        } else if (absContribution > 0.05) {
            return String.format("%s%.3f (Impact MOYEN)", sign, absContribution);
        } else {
            return String.format("%s%.3f (Impact FAIBLE)", sign, absContribution);
        }
    }

    /**
     * Obtient le niveau de confiance
     */
    private String getConfidenceLevel(double probability) {
        double absProb = Math.abs(probability - 0.5);

        if (absProb > 0.4) return "TRÈS ÉLEVÉE 🟢";
        else if (absProb > 0.3) return "ÉLEVÉE 🟢";
        else if (absProb > 0.2) return "MOYENNE 🟡";
        else if (absProb > 0.1) return "FAIBLE 🟠";
        else return "TRÈS FAIBLE 🔴";
    }

    /**
     * Génère une recommandation basée sur la probabilité
     */
    private String generateRecommendation(double probability) {
        if (probability >= 0.85) {
            return "✅ Match hautement probable. Recommandation: ACCEPTER automatiquement.";
        } else if (probability >= 0.7) {
            return "✅ Match probable. Recommandation: ACCEPTER avec vérification légère.";
        } else if (probability >= 0.5) {
            return "⚠️ Match incertain. Recommandation: RÉVISION MANUELLE requise.";
        } else if (probability >= 0.3) {
            return "❌ Pas de match probable. Recommandation: REJETER avec vérification.";
        } else {
            return "❌ Pas de match. Recommandation: REJETER automatiquement.";
        }
    }

    /**
     * Génère une barre de progression ASCII
     */
    private String generateBar(double score, int maxLength) {
        int filledLength = (int) (score * maxLength);
        int emptyLength = maxLength - filledLength;

        return "█".repeat(filledLength) + "░".repeat(emptyLength);
    }

    /**
     * Génère une interprétation des features importantes
     */
    private String generateFeatureInterpretation(FeatureImportance importance) {
        StringBuilder sb = new StringBuilder();

        var topFeature = importance.getRankedFeatures().get(0);
        String topLabel = getFeatureLabel(topFeature.getFeatureName());

        sb.append(String.format(
            "La feature la plus importante est '%s' (score: %.4f).\n",
            topLabel, topFeature.getScore()
        ));

        sb.append("Cela signifie que cette caractéristique a le plus d'influence\n");
        sb.append("sur les décisions du modèle de matching bancaire.");

        return sb.toString();
    }

    /**
     * Tronque un texte à une longueur maximale
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength
            ? text.substring(0, maxLength - 3) + "..."
            : text;
    }
}
