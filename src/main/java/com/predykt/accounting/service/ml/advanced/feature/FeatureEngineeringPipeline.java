package com.predykt.accounting.service.ml.advanced.feature;

import com.predykt.accounting.domain.entity.BankTransaction;
import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.GeneralLedger;
import com.predykt.accounting.dto.ml.AdvancedMatchFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Pipeline d'ingénierie des features ML
 * Architecture: advanced/feature/ (Orchestration)
 *
 * Responsabilités:
 * - Coordonner l'extraction de toutes les features (30+)
 * - Combiner les résultats des 3 extracteurs spécialisés
 * - Générer le DTO final AdvancedMatchFeatures
 * - Gestion centralisée des erreurs
 *
 * Extracteurs utilisés:
 * 1. AdvancedFeatureExtractor: Features numériques (Tier 1 + Tier 3)
 * 2. NLPFeatureExtractor: Features NLP (Tier 2)
 * 3. HistoricalFeatureExtractor: Features historiques (Tier 4)
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureEngineeringPipeline {

    private final AdvancedFeatureExtractor advancedExtractor;
    private final NLPFeatureExtractor nlpExtractor;
    private final HistoricalFeatureExtractor historicalExtractor;

    /**
     * Extraction complète des 30+ features pour un match candidat
     *
     * @param bt BankTransaction candidate
     * @param gl GeneralLedger candidate
     * @param company Société concernée
     * @return AdvancedMatchFeatures avec toutes les features
     */
    public AdvancedMatchFeatures extractAllFeatures(
            BankTransaction bt,
            GeneralLedger gl,
            Company company) {

        long startTime = System.currentTimeMillis();

        try {
            log.debug("🔧 [Pipeline] Extraction features - BT:{} <-> GL:{}",
                     bt.getId(), gl.getId());

            // Extraction par l'extracteur principal (Tier 1 + Tier 3)
            AdvancedMatchFeatures features = advancedExtractor.extractAdvancedFeatures(bt, gl, company);

            // Enrichissement avec features NLP (Tier 2)
            Map<String, Double> nlpFeatures = nlpExtractor.extractNLPFeatures(bt, gl);
            enrichWithNLPFeatures(features, nlpFeatures);

            // Enrichissement avec features historiques (Tier 4)
            Map<String, Double> historicalFeatures = historicalExtractor.extractHistoricalFeatures(
                bt, gl, company
            );
            enrichWithHistoricalFeatures(features, historicalFeatures);

            long extractionTime = System.currentTimeMillis() - startTime;
            log.debug("✅ [Pipeline] Extraction terminée - {}ms", extractionTime);

            return features;

        } catch (Exception e) {
            log.error("❌ [Pipeline] Erreur extraction features - BT:{} GL:{}: {}",
                     bt.getId(), gl.getId(), e.getMessage(), e);

            // Retourner features par défaut en cas d'erreur
            return createDefaultFeatures();
        }
    }

    /**
     * Enrichit le DTO avec les features NLP
     */
    private void enrichWithNLPFeatures(
            AdvancedMatchFeatures features,
            Map<String, Double> nlpFeatures) {

        // Les features NLP sont déjà dans AdvancedFeatureExtractor
        // Cette méthode permet de surcharger/enrichir si besoin
        // Pour l'instant, on log juste pour monitoring

        log.debug("📝 [Pipeline] Features NLP extraites: {}", nlpFeatures.size());
    }

    /**
     * Enrichit le DTO avec les features historiques
     */
    private void enrichWithHistoricalFeatures(
            AdvancedMatchFeatures features,
            Map<String, Double> historicalFeatures) {

        // Les features historiques sont déjà dans AdvancedFeatureExtractor
        // Cette méthode permet de surcharger/enrichir si besoin

        log.debug("📊 [Pipeline] Features historiques extraites: {}", historicalFeatures.size());
    }

    /**
     * Crée un DTO avec des features par défaut (en cas d'erreur)
     */
    private AdvancedMatchFeatures createDefaultFeatures() {
        return AdvancedMatchFeatures.builder()
            // Tier 1: Numeric
            .amountDifference(999999.0)
            .amountRatio(0.0)
            .dateDiffBusinessDays(999.0)
            .dateDiffCalendarDays(999.0)
            .amountLogRatio(0.0)
            .amountPercentileDiff(999.0)
            .dayOfMonthDiff(0.0)
            .weekOfYearDiff(0.0)
            .monthDiff(0.0)
            .quarterDiff(0.0)

            // Tier 2: NLP
            .tfidfCosineSimilarity(0.0)
            .levenshteinDistance(0.0)
            .jaroWinklerSimilarity(0.0)
            .word2vecSimilarity(0.0)
            .bigramOverlap(0.0)
            .trigramOverlap(0.0)
            .fuzzyMatchScore(0.0)
            .entityNameMatch(0.0)

            // Tier 3: Temporal
            .isWeekend(0.0)
            .isMonthEnd(0.0)
            .isMonthStart(0.0)
            .isQuarterEnd(0.0)
            .dayOfWeek(0.0)
            .quarter(0.0)

            // Tier 4: Historical
            .customerMatchRate(0.0)
            .avgMatchingDelay(0.0)
            .accountFrequency(0.0)
            .amountFrequency(0.0)
            .descriptionFrequency(0.0)
            .recentMatchCount(0.0)

            .build();
    }

    /**
     * Extraction batch pour plusieurs paires (BT, GL)
     *
     * @param candidates Liste de paires (BankTransaction, GeneralLedger)
     * @param company Société concernée
     * @return Liste de AdvancedMatchFeatures
     */
    public java.util.List<AdvancedMatchFeatures> extractAllFeaturesBatch(
            java.util.List<Pair<BankTransaction, GeneralLedger>> candidates,
            Company company) {

        log.info("🔧 [Pipeline] Extraction batch - {} candidats", candidates.size());

        return candidates.stream()
            .map(pair -> extractAllFeatures(pair.getFirst(), pair.getSecond(), company))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Classe utilitaire pour paires (BT, GL)
     */
    public static class Pair<F, S> {
        private final F first;
        private final S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        public F getFirst() {
            return first;
        }

        public S getSecond() {
            return second;
        }
    }
}
