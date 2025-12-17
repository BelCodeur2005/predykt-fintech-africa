package com.predykt.accounting.service.ml;

import com.predykt.accounting.domain.entity.BankTransaction;
import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.GeneralLedger;
import com.predykt.accounting.dto.ml.AdvancedMatchFeatures;
import com.predykt.accounting.repository.BankTransactionRepository;
import com.predykt.accounting.repository.GeneralLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracteur de features ML avancées pour matching bancaire
 *
 * Implémente 30+ features réparties en 4 tiers :
 * - Tier 1 (10) : Features numériques avancées
 * - Tier 2 (8)  : Features NLP (TF-IDF, Levenshtein, Jaro-Winkler, N-grams)
 * - Tier 3 (6)  : Features temporelles (weekend, month-end, quarter)
 * - Tier 4 (6)  : Features historiques (taux de match, fréquences)
 *
 * Objectif : Passer de 70-75% à 95%+ accuracy
 *
 * @author PREDYKT ML Team
 * @version 2.0 - Advanced Features
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedFeatureExtractor {

    private final BankTransactionRepository bankTransactionRepository;
    private final GeneralLedgerRepository generalLedgerRepository;

    // Instances NLP réutilisables
    private final LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
    private final JaroWinklerSimilarity jaroWinklerSimilarity = new JaroWinklerSimilarity();

    /**
     * Extrait TOUTES les features avancées (30+)
     */
    public AdvancedMatchFeatures extractAdvancedFeatures(
        BankTransaction bt,
        GeneralLedger gl,
        Company company
    ) {
        return AdvancedMatchFeatures.builder()
            // Tier 1: Features numériques (10)
            .amountDifference(calculateAmountDifference(bt, gl))
            .amountRatio(calculateAmountRatio(bt, gl))
            .dateDiffBusinessDays(calculateBusinessDaysDiff(bt.getTransactionDate(), gl.getEntryDate()))
            .dateDiffCalendarDays(calculateCalendarDaysDiff(bt.getTransactionDate(), gl.getEntryDate()))
            .amountLogRatio(calculateAmountLogRatio(bt, gl))
            .amountPercentileDiff(calculateAmountPercentileDiff(bt, gl, company))
            .dayOfMonthDiff(calculateDayOfMonthDiff(bt.getTransactionDate(), gl.getEntryDate()))
            .weekOfYearDiff(calculateWeekOfYearDiff(bt.getTransactionDate(), gl.getEntryDate()))
            .monthDiff(calculateMonthDiff(bt.getTransactionDate(), gl.getEntryDate()))
            .quarterDiff(calculateQuarterDiff(bt.getTransactionDate(), gl.getEntryDate()))

            // Tier 2: Features NLP (8)
            .tfidfCosineSimilarity(calculateTfidfSimilarity(bt.getDescription(), gl.getDescription()))
            .levenshteinDistance(calculateLevenshteinDistance(bt.getDescription(), gl.getDescription()))
            .jaroWinklerSimilarity(calculateJaroWinklerSimilarity(bt.getDescription(), gl.getDescription()))
            .word2vecSimilarity(0.0)  // TODO: Implémenter Word2Vec (nécessite modèle pré-entraîné)
            .bigramOverlap(calculateNgramOverlap(bt.getDescription(), gl.getDescription(), 2))
            .trigramOverlap(calculateNgramOverlap(bt.getDescription(), gl.getDescription(), 3))
            .fuzzyMatchScore(calculateFuzzyMatchScore(bt.getDescription(), gl.getDescription()))
            .entityNameMatch(checkEntityNameMatch(bt, gl))

            // Tier 3: Features temporelles (6)
            .isWeekend(isWeekend(bt.getTransactionDate()) ? 1.0 : 0.0)
            .isMonthEnd(isMonthEnd(bt.getTransactionDate()) ? 1.0 : 0.0)
            .isMonthStart(isMonthStart(bt.getTransactionDate()) ? 1.0 : 0.0)
            .isQuarterEnd(isQuarterEnd(bt.getTransactionDate()) ? 1.0 : 0.0)
            .dayOfWeek((double) bt.getTransactionDate().getDayOfWeek().getValue())
            .quarter((double) getQuarter(bt.getTransactionDate()))

            // Tier 4: Features historiques (6)
            .customerMatchRate(calculateCustomerMatchRate(bt, company))
            .avgMatchingDelay(calculateAvgMatchingDelay(bt, company))
            .accountFrequency(calculateAccountFrequency(gl, company))
            .amountFrequency(calculateAmountFrequency(bt, company))
            .descriptionFrequency(calculateDescriptionFrequency(bt, company))
            .recentMatchCount(calculateRecentMatchCount(bt, company))

            .build();
    }

    // ==================== TIER 1: FEATURES NUMÉRIQUES ====================

    private Double calculateAmountDifference(BankTransaction bt, GeneralLedger gl) {
        BigDecimal btAmount = bt.getAmount().abs();
        BigDecimal glAmount = gl.getDebitAmount().compareTo(BigDecimal.ZERO) > 0
            ? gl.getDebitAmount()
            : gl.getCreditAmount();
        return btAmount.subtract(glAmount).abs().doubleValue();
    }

    private Double calculateAmountRatio(BankTransaction bt, GeneralLedger gl) {
        BigDecimal btAmount = bt.getAmount().abs();
        BigDecimal glAmount = gl.getDebitAmount().compareTo(BigDecimal.ZERO) > 0
            ? gl.getDebitAmount()
            : gl.getCreditAmount();

        if (glAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }

        return btAmount.divide(glAmount, 4, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    private Double calculateBusinessDaysDiff(LocalDate date1, LocalDate date2) {
        long totalDays = Math.abs(ChronoUnit.DAYS.between(date1, date2));
        long businessDays = 0;

        LocalDate start = date1.isBefore(date2) ? date1 : date2;
        LocalDate end = date1.isBefore(date2) ? date2 : date1;

        while (!start.isAfter(end)) {
            DayOfWeek dayOfWeek = start.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                businessDays++;
            }
            start = start.plusDays(1);
        }

        return (double) businessDays;
    }

    private Double calculateCalendarDaysDiff(LocalDate date1, LocalDate date2) {
        return (double) Math.abs(ChronoUnit.DAYS.between(date1, date2));
    }

    private Double calculateAmountLogRatio(BankTransaction bt, GeneralLedger gl) {
        BigDecimal btAmount = bt.getAmount().abs();
        BigDecimal glAmount = gl.getDebitAmount().compareTo(BigDecimal.ZERO) > 0
            ? gl.getDebitAmount()
            : gl.getCreditAmount();

        if (btAmount.compareTo(BigDecimal.ZERO) == 0 || glAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }

        double logBt = Math.log(btAmount.doubleValue());
        double logGl = Math.log(glAmount.doubleValue());

        return Math.abs(logBt - logGl);
    }

    private Double calculateAmountPercentileDiff(BankTransaction bt, GeneralLedger gl, Company company) {
        // TODO: Implémenter calcul de percentile (nécessite stats sur tous les montants)
        // Pour l'instant, retourner 0.0
        return 0.0;
    }

    private Double calculateDayOfMonthDiff(LocalDate date1, LocalDate date2) {
        return (double) Math.abs(date1.getDayOfMonth() - date2.getDayOfMonth());
    }

    private Double calculateWeekOfYearDiff(LocalDate date1, LocalDate date2) {
        int week1 = date1.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int week2 = date2.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return (double) Math.abs(week1 - week2);
    }

    private Double calculateMonthDiff(LocalDate date1, LocalDate date2) {
        return (double) Math.abs(ChronoUnit.MONTHS.between(date1, date2));
    }

    private Double calculateQuarterDiff(LocalDate date1, LocalDate date2) {
        int q1 = getQuarter(date1);
        int q2 = getQuarter(date2);
        return (double) Math.abs(q1 - q2);
    }

    // ==================== TIER 2: FEATURES NLP ====================

    private Double calculateTfidfSimilarity(String desc1, String desc2) {
        if (desc1 == null || desc2 == null) {
            return 0.0;
        }

        // TF-IDF simplifié : utiliser le cosine similarity des mots
        Set<String> words1 = tokenize(desc1);
        Set<String> words2 = tokenize(desc2);

        if (words1.isEmpty() || words2.isEmpty()) {
            return 0.0;
        }

        // Intersection et union
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        return (double) intersection.size() / union.size();
    }

    private Double calculateLevenshteinDistance(String desc1, String desc2) {
        if (desc1 == null || desc2 == null) {
            return 999.0;  // Distance maximale
        }

        String normalized1 = normalize(desc1);
        String normalized2 = normalize(desc2);

        Integer distance = levenshteinDistance.apply(normalized1, normalized2);
        return distance != null ? distance.doubleValue() : 999.0;
    }

    private Double calculateJaroWinklerSimilarity(String desc1, String desc2) {
        if (desc1 == null || desc2 == null) {
            return 0.0;
        }

        String normalized1 = normalize(desc1);
        String normalized2 = normalize(desc2);

        return jaroWinklerSimilarity.apply(normalized1, normalized2);
    }

    private Double calculateNgramOverlap(String desc1, String desc2, int n) {
        if (desc1 == null || desc2 == null) {
            return 0.0;
        }

        Set<String> ngrams1 = extractNgrams(desc1, n);
        Set<String> ngrams2 = extractNgrams(desc2, n);

        if (ngrams1.isEmpty() || ngrams2.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(ngrams1);
        intersection.retainAll(ngrams2);

        return (double) intersection.size() / Math.max(ngrams1.size(), ngrams2.size());
    }

    private Double calculateFuzzyMatchScore(String desc1, String desc2) {
        // Combinaison de Levenshtein + Jaro-Winkler
        double levenshtein = calculateLevenshteinDistance(desc1, desc2);
        double jaroWinkler = calculateJaroWinklerSimilarity(desc1, desc2);

        // Normaliser Levenshtein (inverse et normalise)
        int maxLen = Math.max(
            desc1 != null ? desc1.length() : 0,
            desc2 != null ? desc2.length() : 0
        );
        double normalizedLevenshtein = maxLen > 0 ? 1.0 - (levenshtein / maxLen) : 0.0;

        // Moyenne pondérée
        return 0.6 * jaroWinkler + 0.4 * normalizedLevenshtein;
    }

    private Double checkEntityNameMatch(BankTransaction bt, GeneralLedger gl) {
        String btThirdParty = bt.getThirdPartyName();
        String glThirdParty = gl.getTiersName();

        if (btThirdParty == null || glThirdParty == null) {
            return 0.0;
        }

        String normalized1 = normalize(btThirdParty);
        String normalized2 = normalize(glThirdParty);

        return normalized1.equals(normalized2) ? 1.0 : 0.0;
    }

    // ==================== TIER 3: FEATURES TEMPORELLES ====================

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private boolean isMonthEnd(LocalDate date) {
        return date.getDayOfMonth() >= 28;
    }

    private boolean isMonthStart(LocalDate date) {
        return date.getDayOfMonth() <= 3;
    }

    private boolean isQuarterEnd(LocalDate date) {
        int month = date.getMonthValue();
        return (month == 3 || month == 6 || month == 9 || month == 12) && isMonthEnd(date);
    }

    private int getQuarter(LocalDate date) {
        return (date.getMonthValue() - 1) / 3 + 1;
    }

    // ==================== TIER 4: FEATURES HISTORIQUES ====================

    private Double calculateCustomerMatchRate(BankTransaction bt, Company company) {
        // TODO: Implémenter calcul du taux de match historique pour ce client
        // Nécessite requêtes sur l'historique des transactions
        return 0.0;
    }

    private Double calculateAvgMatchingDelay(BankTransaction bt, Company company) {
        // TODO: Implémenter calcul du délai moyen de matching
        return 0.0;
    }

    private Double calculateAccountFrequency(GeneralLedger gl, Company company) {
        // TODO: Implémenter fréquence d'utilisation du compte
        return 0.0;
    }

    private Double calculateAmountFrequency(BankTransaction bt, Company company) {
        // TODO: Implémenter fréquence de ce montant dans l'historique
        return 0.0;
    }

    private Double calculateDescriptionFrequency(BankTransaction bt, Company company) {
        // TODO: Implémenter fréquence de cette description
        return 0.0;
    }

    private Double calculateRecentMatchCount(BankTransaction bt, Company company) {
        // TODO: Implémenter comptage des matches récents
        return 0.0;
    }

    // ==================== UTILITAIRES NLP ====================

    private Set<String> tokenize(String text) {
        if (text == null) {
            return Collections.emptySet();
        }

        return Arrays.stream(normalize(text).split("\\s+"))
            .filter(word -> word.length() > 2)  // Ignorer mots courts
            .collect(Collectors.toSet());
    }

    private Set<String> extractNgrams(String text, int n) {
        if (text == null || n <= 0) {
            return Collections.emptySet();
        }

        String normalized = normalize(text);
        Set<String> ngrams = new HashSet<>();

        if (normalized.length() < n) {
            return ngrams;
        }

        for (int i = 0; i <= normalized.length() - n; i++) {
            ngrams.add(normalized.substring(i, i + n));
        }

        return ngrams;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        return text.toLowerCase().trim()
            .replaceAll("[àáâãäå]", "a")
            .replaceAll("[èéêë]", "e")
            .replaceAll("[ìíîï]", "i")
            .replaceAll("[òóôõö]", "o")
            .replaceAll("[ùúûü]", "u")
            .replaceAll("[ç]", "c")
            .replaceAll("[^a-z0-9\\s]", "");  // Supprime ponctuation
    }
}
