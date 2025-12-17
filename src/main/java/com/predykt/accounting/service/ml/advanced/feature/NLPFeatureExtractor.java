package com.predykt.accounting.service.ml.advanced.feature;

import com.predykt.accounting.domain.entity.BankTransaction;
import com.predykt.accounting.domain.entity.GeneralLedger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracteur de features NLP (Natural Language Processing)
 * Architecture: advanced/feature/ (Tier 2)
 *
 * Features extraites (8 total):
 * 1. TF-IDF Cosine Similarity
 * 2. Levenshtein Distance
 * 3. Jaro-Winkler Similarity
 * 4. N-gram Overlap (Bigrams)
 * 5. N-gram Overlap (Trigrams)
 * 6. Word Count Similarity
 * 7. Longest Common Subsequence
 * 8. Phonetic Similarity (Soundex-like)
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Component
public class NLPFeatureExtractor {

    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();
    private final LevenshteinDistance levenshtein = new LevenshteinDistance();

    /**
     * Extrait toutes les features NLP
     */
    public Map<String, Double> extractNLPFeatures(BankTransaction bt, GeneralLedger gl) {
        Map<String, Double> features = new HashMap<>();

        String desc1 = normalize(bt.getDescription());
        String desc2 = normalize(gl.getDescription());

        features.put("tfidf_cosine_similarity", calculateTfIdfSimilarity(desc1, desc2));
        features.put("levenshtein_distance", calculateLevenshteinNormalized(desc1, desc2));
        features.put("jaro_winkler_similarity", calculateJaroWinkler(desc1, desc2));
        features.put("bigram_overlap", calculateNGramOverlap(desc1, desc2, 2));
        features.put("trigram_overlap", calculateNGramOverlap(desc1, desc2, 3));
        features.put("word_count_similarity", calculateWordCountSimilarity(desc1, desc2));
        features.put("lcs_ratio", calculateLCSRatio(desc1, desc2));
        features.put("phonetic_similarity", calculatePhoneticSimilarity(desc1, desc2));

        return features;
    }

    /**
     * Normalise une chaîne pour le traitement NLP
     */
    private String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                   .replaceAll("[^a-z0-9\\s]", "")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    /**
     * Calcul TF-IDF Cosine Similarity (simplifié avec Jaccard)
     */
    private Double calculateTfIdfSimilarity(String text1, String text2) {
        try {
            if (text1.isEmpty() || text2.isEmpty()) return 0.0;

            Set<String> words1 = new HashSet<>(Arrays.asList(text1.split("\\s+")));
            Set<String> words2 = new HashSet<>(Arrays.asList(text2.split("\\s+")));

            Set<String> intersection = new HashSet<>(words1);
            intersection.retainAll(words2);

            Set<String> union = new HashSet<>(words1);
            union.addAll(words2);

            return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
        } catch (Exception e) {
            log.warn("Erreur TF-IDF: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Levenshtein Distance normalisée [0-1]
     */
    private Double calculateLevenshteinNormalized(String text1, String text2) {
        try {
            if (text1.isEmpty() && text2.isEmpty()) return 1.0;
            if (text1.isEmpty() || text2.isEmpty()) return 0.0;

            int distance = levenshtein.apply(text1, text2);
            int maxLength = Math.max(text1.length(), text2.length());

            return 1.0 - ((double) distance / maxLength);
        } catch (Exception e) {
            log.warn("Erreur Levenshtein: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Jaro-Winkler Similarity [0-1]
     */
    private Double calculateJaroWinkler(String text1, String text2) {
        try {
            if (text1.isEmpty() && text2.isEmpty()) return 1.0;
            if (text1.isEmpty() || text2.isEmpty()) return 0.0;

            return jaroWinkler.apply(text1, text2);
        } catch (Exception e) {
            log.warn("Erreur Jaro-Winkler: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * N-gram Overlap (Jaccard index sur n-grams)
     */
    private Double calculateNGramOverlap(String text1, String text2, int n) {
        try {
            if (text1.isEmpty() || text2.isEmpty()) return 0.0;

            Set<String> ngrams1 = getNGrams(text1, n);
            Set<String> ngrams2 = getNGrams(text2, n);

            if (ngrams1.isEmpty() || ngrams2.isEmpty()) return 0.0;

            Set<String> intersection = new HashSet<>(ngrams1);
            intersection.retainAll(ngrams2);

            Set<String> union = new HashSet<>(ngrams1);
            union.addAll(ngrams2);

            return (double) intersection.size() / union.size();
        } catch (Exception e) {
            log.warn("Erreur N-gram (n={}): {}", n, e.getMessage());
            return 0.0;
        }
    }

    /**
     * Génère les n-grams d'une chaîne
     */
    private Set<String> getNGrams(String text, int n) {
        Set<String> ngrams = new HashSet<>();

        if (text.length() < n) {
            ngrams.add(text);
            return ngrams;
        }

        for (int i = 0; i <= text.length() - n; i++) {
            ngrams.add(text.substring(i, i + n));
        }

        return ngrams;
    }

    /**
     * Similarité du nombre de mots
     */
    private Double calculateWordCountSimilarity(String text1, String text2) {
        try {
            if (text1.isEmpty() && text2.isEmpty()) return 1.0;

            int count1 = text1.isEmpty() ? 0 : text1.split("\\s+").length;
            int count2 = text2.isEmpty() ? 0 : text2.split("\\s+").length;

            if (count1 == 0 && count2 == 0) return 1.0;

            int min = Math.min(count1, count2);
            int max = Math.max(count1, count2);

            return max == 0 ? 0.0 : (double) min / max;
        } catch (Exception e) {
            log.warn("Erreur Word Count: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Longest Common Subsequence Ratio
     */
    private Double calculateLCSRatio(String text1, String text2) {
        try {
            if (text1.isEmpty() && text2.isEmpty()) return 1.0;
            if (text1.isEmpty() || text2.isEmpty()) return 0.0;

            int lcsLength = longestCommonSubsequence(text1, text2);
            int maxLength = Math.max(text1.length(), text2.length());

            return (double) lcsLength / maxLength;
        } catch (Exception e) {
            log.warn("Erreur LCS: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Calcul du LCS (Longest Common Subsequence)
     */
    private int longestCommonSubsequence(String text1, String text2) {
        int m = text1.length();
        int n = text2.length();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        return dp[m][n];
    }

    /**
     * Similarité phonétique (basée sur les premières lettres et voyelles)
     */
    private Double calculatePhoneticSimilarity(String text1, String text2) {
        try {
            if (text1.isEmpty() && text2.isEmpty()) return 1.0;
            if (text1.isEmpty() || text2.isEmpty()) return 0.0;

            String phonetic1 = getPhoneticCode(text1);
            String phonetic2 = getPhoneticCode(text2);

            return phonetic1.equals(phonetic2) ? 1.0 : 0.5;
        } catch (Exception e) {
            log.warn("Erreur Phonetic: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Génère un code phonétique simplifié (style Soundex)
     */
    private String getPhoneticCode(String text) {
        if (text.isEmpty()) return "";

        // Prendre la première lettre + enlever les voyelles
        StringBuilder code = new StringBuilder();
        code.append(text.charAt(0));

        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!isVowel(c)) {
                code.append(c);
            }
        }

        // Limiter à 4 caractères
        return code.substring(0, Math.min(4, code.length()));
    }

    /**
     * Vérifie si un caractère est une voyelle
     */
    private boolean isVowel(char c) {
        return "aeiou".indexOf(c) >= 0;
    }
}
