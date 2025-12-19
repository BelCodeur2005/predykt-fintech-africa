package com.predykt.accounting.service.ml.advanced.explainability;

import com.predykt.accounting.domain.entity.BankTransaction;
import com.predykt.accounting.domain.entity.GeneralLedger;
import com.predykt.accounting.dto.ml.FeatureImportance;
import com.predykt.accounting.dto.ml.SHAPExplanation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Service de génération de rapports d'explication ML
 * Format conforme au document ML_BANK_RECONCILIATION_ADVANCED.md
 *
 * Génère des rapports lisibles au format EXACT spécifié:
 * - Explication SHAP avec impact quantifié en %
 * - Top N features avec impact détaillé
 * - Format professionnel pour utilisateurs métier
 *
 * @author PREDYKT ML Team
 * @version 2.0 - Format Document Compliance
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExplanationReportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Génère un rapport d'explication SHAP au format EXACT du document
     *
     * Format attendu (ML_BANK_RECONCILIATION_ADVANCED.md lignes 1428-1477):
     *
     * 🔍 EXPLICATION (SHAP):
     *
     * Top 5 facteurs ayant influencé cette prédiction:
     *
     * 1. 💰 amount_difference = 0.00 FCFA
     *    ➜ Impact: +28.5% (forte augmentation probabilité)
     *
     * 2. 📝 tfidf_similarity = 0.94
     *    ➜ Impact: +22.3% (descriptions très similaires)
     */
    public String generateSHAPExplanationReport(
            SHAPExplanation explanation,
            BankTransaction bt,
            GeneralLedger gl
    ) {
        StringBuilder sb = new StringBuilder();

        // En-tête du rapport complet
        sb.append("╔════════════════════════════════════════════════════════════════╗\n");
        sb.append("║         PRÉDICTION RAPPROCHEMENT BANCAIRE ML v2.0              ║\n");
        sb.append("╚════════════════════════════════════════════════════════════════╝\n\n");

        // Transaction Bancaire
        sb.append("Transaction Bancaire:\n");
        sb.append(String.format("  • Montant: %,d FCFA\n", bt.getAmount().longValue()));
        sb.append(String.format("  • Date: %s\n", bt.getTransactionDate().format(DATE_FORMATTER)));
        sb.append(String.format("  • Description: \"%s\"\n\n", bt.getDescription()));

        // Écriture Comptable
        sb.append("Écriture Comptable Candidate:\n");
        sb.append(String.format("  • Montant: %,d FCFA\n",
                gl.getDebitAmount().subtract(gl.getCreditAmount()).abs().longValue()));
        sb.append(String.format("  • Date: %s\n", gl.getEntryDate().format(DATE_FORMATTER)));
        sb.append(String.format("  • Compte: %s - %s\n",
                gl.getAccount().getAccountNumber(),
                gl.getAccount().getAccountName()));
        sb.append(String.format("  • Description: \"%s\"\n\n", gl.getDescription()));

        // Résultat Prédiction
        sb.append("┌────────────────────────────────────────────────────────────────┐\n");
        sb.append("│                    RÉSULTAT PRÉDICTION                         │\n");
        sb.append("├────────────────────────────────────────────────────────────────┤\n");

        boolean isMatch = explanation.getProbability() >= 0.5;
        sb.append(String.format("│  %s MATCH DÉTECTÉ                                              │\n",
                isMatch ? "✅" : "❌"));
        sb.append(String.format("│  Confiance: %.1f%%                                              │\n",
                explanation.getConfidence()));
        sb.append(String.format("│  Algorithme: %s                         │\n",
                padRight(explanation.getModelVersion(), 32)));
        sb.append("└────────────────────────────────────────────────────────────────┘\n\n");

        // EXPLICATION SHAP (FORMAT EXACT DU DOCUMENT)
        sb.append("🔍 EXPLICATION (SHAP):\n\n");
        sb.append("Top 5 facteurs ayant influencé cette prédiction:\n\n");

        int rank = 1;
        for (FeatureImportance feat : explanation.getTopFeatures().subList(0, Math.min(5, explanation.getTopFeatures().size()))) {
            // Emoji selon le tier
            String emoji = getEmojiForTier(feat.getTier());

            // Nom lisible et valeur
            String featureLine = String.format("%d. %s %s = %.2f%s",
                    rank,
                    emoji,
                    feat.getFeatureDisplayName(),
                    feat.getFeatureValue(),
                    feat.getUnit());

            sb.append(featureLine).append("\n");

            // Impact avec direction
            String direction = feat.isPositiveImpact() ? "augmentation" : "diminution";
            String description = getFeatureImpactDescription(feat.getFeatureName(), feat.getFeatureValue());

            sb.append(String.format("   ➜ Impact: %s%.1f%% (%s)\n\n",
                    feat.isPositiveImpact() ? "+" : "",
                    feat.getImpactPercent(),
                    description));

            rank++;
        }

        // Action recommandée
        sb.append("Action recommandée: ");
        if (explanation.getConfidence() >= 95.0) {
            sb.append("✅ Valider automatiquement ce match\n");
        } else if (explanation.getConfidence() >= 80.0) {
            sb.append("✅ Valider ce match (révision légère recommandée)\n");
        } else if (explanation.getConfidence() >= 60.0) {
            sb.append("⚠️ Révision manuelle requise\n");
        } else {
            sb.append("❌ Rejeter ce match\n");
        }

        return sb.toString();
    }

    /**
     * Génère une explication courte pour l'API
     */
    public String generateShortExplanation(SHAPExplanation explanation) {
        if (explanation.getTopFeatures().isEmpty()) {
            return "🤖 Prédiction ML basée sur modèle " + explanation.getModelVersion();
        }

        StringBuilder sb = new StringBuilder("🔍 EXPLICATION (SHAP):\n\n");
        sb.append("Top 5 facteurs:\n\n");

        int rank = 1;
        for (FeatureImportance feat : explanation.getTopFeatures().subList(0, Math.min(5, explanation.getTopFeatures().size()))) {
            sb.append(String.format("%d. %s %s = %.2f%s\n",
                    rank,
                    getEmojiForTier(feat.getTier()),
                    feat.getFeatureDisplayName(),
                    feat.getFeatureValue(),
                    feat.getUnit()));

            sb.append(String.format("   ➜ Impact: %s%.1f%%\n\n",
                    feat.isPositiveImpact() ? "+" : "",
                    feat.getImpactPercent()));

            rank++;
        }

        return sb.toString();
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
     * Pad string à droite
     */
    private String padRight(String text, int length) {
        if (text.length() >= length) {
            return text.substring(0, length);
        }
        return text + " ".repeat(length - text.length());
    }
}
