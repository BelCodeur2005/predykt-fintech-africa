package com.predykt.accounting.service.ml.advanced.feature;

import com.predykt.accounting.domain.entity.BankTransaction;
import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.GeneralLedger;
import com.predykt.accounting.repository.BankTransactionRepository;
import com.predykt.accounting.repository.GeneralLedgerRepository;
import com.predykt.accounting.repository.ml.MLPredictionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Extracteur de features historiques (Historical Context)
 * Architecture: advanced/feature/ (Tier 4)
 *
 * Features extraites (6 total):
 * 1. Customer Match Rate: Taux de match historique pour ce client
 * 2. Avg Matching Delay: Délai moyen de matching (jours)
 * 3. Account Frequency: Fréquence d'utilisation du compte (90j)
 * 4. Amount Frequency: Fréquence du montant similaire (90j)
 * 5. Description Frequency: Fréquence de la description (90j)
 * 6. Recent Match Count: Nombre de matches récents (30j)
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoricalFeatureExtractor {

    private final MLPredictionLogRepository mlPredictionLogRepository;
    private final GeneralLedgerRepository generalLedgerRepository;
    private final BankTransactionRepository bankTransactionRepository;

    /**
     * Extrait toutes les features historiques
     */
    public Map<String, Double> extractHistoricalFeatures(
            BankTransaction bt,
            GeneralLedger gl,
            Company company) {

        Map<String, Double> features = new HashMap<>();

        features.put("customer_match_rate", calculateCustomerMatchRate(bt, company));
        features.put("avg_matching_delay", calculateAvgMatchingDelay(company));
        features.put("account_frequency", calculateAccountFrequency(gl, company));
        features.put("amount_frequency", calculateAmountFrequency(bt, company));
        features.put("description_frequency", calculateDescriptionFrequency(bt, company));
        features.put("recent_match_count", calculateRecentMatchCount(company));

        return features;
    }

    /**
     * Taux de match historique pour ce client/fournisseur
     */
    private Double calculateCustomerMatchRate(BankTransaction bt, Company company) {
        try {
            String thirdPartyName = bt.getThirdPartyName();
            if (thirdPartyName == null || thirdPartyName.trim().isEmpty()) {
                return 0.0;
            }

            Double matchRate = mlPredictionLogRepository.calculateCustomerMatchRate(
                company,
                thirdPartyName
            );

            return matchRate != null ? matchRate : 0.0;
        } catch (Exception e) {
            log.warn("Erreur calcul customerMatchRate: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Délai moyen entre date BT et date GL pour les matches réussis
     */
    private Double calculateAvgMatchingDelay(Company company) {
        try {
            Double avgDelay = mlPredictionLogRepository.calculateAvgMatchingDelay(company);
            return avgDelay != null ? avgDelay : 0.0;
        } catch (Exception e) {
            log.warn("Erreur calcul avgMatchingDelay: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Fréquence d'utilisation du compte dans les 90 derniers jours
     */
    private Double calculateAccountFrequency(GeneralLedger gl, Company company) {
        try {
            LocalDate since = LocalDate.now().minusDays(90);
            long frequency = generalLedgerRepository.countAccountUsage(
                company,
                gl.getAccount(),
                since
            );

            return (double) frequency;
        } catch (Exception e) {
            log.warn("Erreur calcul accountFrequency: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Fréquence du montant similaire (±5%) dans les 90 derniers jours
     */
    private Double calculateAmountFrequency(BankTransaction bt, Company company) {
        try {
            BigDecimal amount = bt.getAmount().abs();
            LocalDate since = LocalDate.now().minusDays(90);

            long frequency = bankTransactionRepository.countSimilarAmounts(
                company,
                amount,
                since
            );

            return (double) frequency;
        } catch (Exception e) {
            log.warn("Erreur calcul amountFrequency: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Fréquence de la description dans les 90 derniers jours
     */
    private Double calculateDescriptionFrequency(BankTransaction bt, Company company) {
        try {
            String description = bt.getDescription();
            if (description == null || description.trim().isEmpty()) {
                return 0.0;
            }

            // Extraire le premier mot significatif (>3 chars)
            String keyword = extractKeyword(description);
            if (keyword.isEmpty()) {
                return 0.0;
            }

            LocalDate since = LocalDate.now().minusDays(90);

            long frequency = bankTransactionRepository.countSimilarDescriptions(
                company,
                keyword,
                since
            );

            return (double) frequency;
        } catch (Exception e) {
            log.warn("Erreur calcul descriptionFrequency: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Nombre de matches réussis dans les 30 derniers jours
     */
    private Double calculateRecentMatchCount(Company company) {
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(30);

            long recentMatches = mlPredictionLogRepository.countRecentMatches(
                company,
                since
            );

            return (double) recentMatches;
        } catch (Exception e) {
            log.warn("Erreur calcul recentMatchCount: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Extrait le premier mot significatif d'une description
     */
    private String extractKeyword(String description) {
        if (description == null) return "";

        String normalized = description.toLowerCase()
                                      .replaceAll("[^a-z0-9\\s]", "")
                                      .trim();

        String[] words = normalized.split("\\s+");

        for (String word : words) {
            if (word.length() > 3) {  // Mot significatif (>3 chars)
                return word;
            }
        }

        return words.length > 0 ? words[0] : "";
    }
}
