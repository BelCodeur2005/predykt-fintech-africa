package com.predykt.accounting.service;

import com.predykt.accounting.domain.entity.ChartOfAccounts;
import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.Customer;
import com.predykt.accounting.domain.entity.Supplier;
import com.predykt.accounting.domain.enums.AccountType;
import com.predykt.accounting.repository.ChartOfAccountsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de gestion des sous-comptes auxiliaires (Plan de Tiers)
 * Conforme OHADA - Auto-génération des comptes clients et fournisseurs
 *
 * Fonctionnalités:
 * - Génération automatique de sous-comptes clients (4111001, 4111002...)
 * - Génération automatique de sous-comptes fournisseurs (4011001, 4011002...)
 * - Utilisation de séquences PostgreSQL pour la numérotation
 * - Intégration avec le plan comptable OHADA
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TiersAccountService {

    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final JdbcTemplate jdbcTemplate;

    // Constantes OHADA
    private static final String CUSTOMER_PARENT_ACCOUNT = "411";  // CLIENTS
    private static final String SUPPLIER_PARENT_ACCOUNT = "401";  // FOURNISSEURS
    private static final String CUSTOMER_ACCOUNT_PREFIX = "4111";
    private static final String SUPPLIER_ACCOUNT_PREFIX = "4011";

    /**
     * Crée un sous-compte auxiliaire pour un client
     * Génère automatiquement le numéro de compte (4111001, 4111002...)
     *
     * @param company Entreprise
     * @param customer Client
     * @return Compte auxiliaire créé
     */
    public ChartOfAccounts createCustomerAuxiliaryAccount(Company company, Customer customer) {
        log.info("🔧 Création sous-compte client pour: {} (Entreprise: {})",
            customer.getName(), company.getName());

        // 1. Récupérer le prochain numéro de séquence
        Long sequence = getNextCustomerSequence();

        // 2. Formater le numéro de compte: 4111 + séquence sur 3 chiffres
        String accountNumber = String.format("%s%03d", CUSTOMER_ACCOUNT_PREFIX, sequence);

        // 3. Vérifier que le compte n'existe pas déjà (sécurité)
        if (chartOfAccountsRepository.findByCompanyAndAccountNumber(company, accountNumber).isPresent()) {
            log.warn("⚠️ Le compte {} existe déjà ! Tentative avec la séquence suivante...", accountNumber);
            sequence = getNextCustomerSequence();
            accountNumber = String.format("%s%03d", CUSTOMER_ACCOUNT_PREFIX, sequence);
        }

        // 4. Créer le compte dans chart_of_accounts
        ChartOfAccounts account = ChartOfAccounts.builder()
            .company(company)
            .accountNumber(accountNumber)
            .accountName("CLIENT - " + customer.getName())
            .accountType(AccountType.ACTIF_CIRCULANT)  // Clients = Actif circulant (créances)
            .isActive(true)
            .build();

        ChartOfAccounts saved = chartOfAccountsRepository.save(account);

        log.info("✅ Sous-compte client créé: {} - {}", saved.getAccountNumber(), saved.getAccountName());

        return saved;
    }

    /**
     * Crée un sous-compte auxiliaire pour un fournisseur
     * Génère automatiquement le numéro de compte (4011001, 4011002...)
     *
     * @param company Entreprise
     * @param supplier Fournisseur
     * @return Compte auxiliaire créé
     */
    public ChartOfAccounts createSupplierAuxiliaryAccount(Company company, Supplier supplier) {
        log.info("🔧 Création sous-compte fournisseur pour: {} (Entreprise: {})",
            supplier.getName(), company.getName());

        // 1. Récupérer le prochain numéro de séquence
        Long sequence = getNextSupplierSequence();

        // 2. Formater le numéro de compte: 4011 + séquence sur 3 chiffres
        String accountNumber = String.format("%s%03d", SUPPLIER_ACCOUNT_PREFIX, sequence);

        // 3. Vérifier que le compte n'existe pas déjà (sécurité)
        if (chartOfAccountsRepository.findByCompanyAndAccountNumber(company, accountNumber).isPresent()) {
            log.warn("⚠️ Le compte {} existe déjà ! Tentative avec la séquence suivante...", accountNumber);
            sequence = getNextSupplierSequence();
            accountNumber = String.format("%s%03d", SUPPLIER_ACCOUNT_PREFIX, sequence);
        }

        // 4. Créer le compte dans chart_of_accounts
        ChartOfAccounts account = ChartOfAccounts.builder()
            .company(company)
            .accountNumber(accountNumber)
            .accountName("FOURNISSEUR - " + supplier.getName())
            .accountType(AccountType.DETTES)  // Fournisseurs = Dettes (passif circulant)
            .isActive(true)
            .build();

        ChartOfAccounts saved = chartOfAccountsRepository.save(account);

        log.info("✅ Sous-compte fournisseur créé: {} - {}", saved.getAccountNumber(), saved.getAccountName());

        return saved;
    }

    /**
     * Récupère le prochain numéro de séquence pour les clients
     * Utilise la séquence PostgreSQL seq_customer_account_number
     *
     * @return Prochain numéro de séquence
     */
    private Long getNextCustomerSequence() {
        return jdbcTemplate.queryForObject(
            "SELECT nextval('seq_customer_account_number')",
            Long.class
        );
    }

    /**
     * Récupère le prochain numéro de séquence pour les fournisseurs
     * Utilise la séquence PostgreSQL seq_supplier_account_number
     *
     * @return Prochain numéro de séquence
     */
    private Long getNextSupplierSequence() {
        return jdbcTemplate.queryForObject(
            "SELECT nextval('seq_supplier_account_number')",
            Long.class
        );
    }

    /**
     * Réinitialise la séquence des clients (DANGER - À utiliser uniquement en développement)
     */
    public void resetCustomerSequence() {
        log.warn("⚠️ RÉINITIALISATION de la séquence des clients !");
        jdbcTemplate.execute("ALTER SEQUENCE seq_customer_account_number RESTART WITH 1");
    }

    /**
     * Réinitialise la séquence des fournisseurs (DANGER - À utiliser uniquement en développement)
     */
    public void resetSupplierSequence() {
        log.warn("⚠️ RÉINITIALISATION de la séquence des fournisseurs !");
        jdbcTemplate.execute("ALTER SEQUENCE seq_supplier_account_number RESTART WITH 1");
    }

    /**
     * Récupère la valeur actuelle de la séquence des clients
     */
    public Long getCurrentCustomerSequenceValue() {
        return jdbcTemplate.queryForObject(
            "SELECT last_value FROM seq_customer_account_number",
            Long.class
        );
    }

    /**
     * Récupère la valeur actuelle de la séquence des fournisseurs
     */
    public Long getCurrentSupplierSequenceValue() {
        return jdbcTemplate.queryForObject(
            "SELECT last_value FROM seq_supplier_account_number",
            Long.class
        );
    }
}
