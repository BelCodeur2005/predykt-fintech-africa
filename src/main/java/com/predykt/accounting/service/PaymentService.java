package com.predykt.accounting.service;

import com.predykt.accounting.domain.entity.*;
import com.predykt.accounting.domain.enums.InvoiceStatus;
import com.predykt.accounting.domain.enums.PaymentMethod;
import com.predykt.accounting.domain.enums.PaymentStatus;
import com.predykt.accounting.domain.enums.PaymentType;
import com.predykt.accounting.dto.request.JournalEntryLineRequest;
import com.predykt.accounting.dto.request.JournalEntryRequest;
import com.predykt.accounting.dto.request.PaymentCreateRequest;
import com.predykt.accounting.dto.response.PaymentResponse;
import com.predykt.accounting.exception.ResourceNotFoundException;
import com.predykt.accounting.exception.ValidationException;
import com.predykt.accounting.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service de gestion des paiements
 * Conforme OHADA avec lettrage automatique
 *
 * Fonctionnalités:
 * - Enregistrement paiements clients (encaissements)
 * - Enregistrement paiements fournisseurs (décaissements)
 * - Lettrage automatique facture ↔ paiement
 * - Génération écritures comptables
 * - Mise à jour statuts factures
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillRepository billRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final CompanyRepository companyRepository;
    private final GeneralLedgerRepository generalLedgerRepository;
    private final GeneralLedgerService generalLedgerService;
    private final JdbcTemplate jdbcTemplate;

    // Constantes
    private static final String PAYMENT_PREFIX = "PAY";
    private static final String BANK_ACCOUNT_DEFAULT = "521";  // Banque en FCFA

    /**
     * Enregistrer un paiement client (encaissement)
     */
    public PaymentResponse recordCustomerPayment(Long companyId, PaymentCreateRequest request) {
        log.info("💰 Enregistrement paiement client pour facture {} - Montant: {} XAF",
            request.getInvoiceId(), request.getAmount());

        Company company = findCompanyOrThrow(companyId);

        // Récupérer la facture
        Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
            .orElseThrow(() -> new ResourceNotFoundException("Facture non trouvée: " + request.getInvoiceId()));

        if (!invoice.getCompany().getId().equals(companyId)) {
            throw new ValidationException("La facture n'appartient pas à cette entreprise");
        }

        // Valider le montant
        if (request.getAmount().compareTo(invoice.getAmountDue()) > 0) {
            throw new ValidationException("Le montant du paiement dépasse le montant dû");
        }

        // Générer numéro de paiement
        String paymentNumber = generatePaymentNumber();

        // Créer le paiement
        Payment payment = Payment.builder()
            .company(company)
            .paymentNumber(paymentNumber)
            .paymentType(PaymentType.CUSTOMER_PAYMENT)
            .invoice(invoice)
            .customer(invoice.getCustomer())
            .paymentDate(request.getPaymentDate())
            .amount(request.getAmount())
            .paymentMethod(request.getPaymentMethod())
            .bankAccountId(request.getBankAccountId())
            .chequeNumber(request.getChequeNumber())
            .mobileMoneyNumber(request.getMobileMoneyNumber())
            .transactionReference(request.getTransactionReference())
            .description(request.getDescription())
            .notes(request.getNotes())
            .status(PaymentStatus.COMPLETED)
            .build();

        payment = paymentRepository.save(payment);

        // Mettre à jour la facture
        invoice.recordPayment(request.getAmount());
        invoiceRepository.save(invoice);

        // Lettrage automatique si totalement payé
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            payment.setIsReconciled(true);
            payment.setReconciliationDate(LocalDate.now());
            invoice.setIsReconciled(true);
            invoice.setReconciliationDate(LocalDate.now());
            paymentRepository.save(payment);
            invoiceRepository.save(invoice);
            log.info("✅ Lettrage automatique effectué pour facture {}", invoice.getInvoiceNumber());
        }

        // Générer l'écriture comptable
        GeneralLedger entry = generateCustomerPaymentEntry(payment, invoice);
        payment.setGeneralLedger(entry);
        paymentRepository.save(payment);

        log.info("✅ Paiement client {} enregistré - Facture {} - Statut: {}",
            payment.getPaymentNumber(), invoice.getInvoiceNumber(), invoice.getStatus());

        return toResponse(payment);
    }

    /**
     * Enregistrer un paiement fournisseur (décaissement)
     */
    public PaymentResponse recordSupplierPayment(Long companyId, PaymentCreateRequest request) {
        log.info("💸 Enregistrement paiement fournisseur pour facture {} - Montant: {} XAF",
            request.getBillId(), request.getAmount());

        Company company = findCompanyOrThrow(companyId);

        // Récupérer la facture fournisseur
        Bill bill = billRepository.findById(request.getBillId())
            .orElseThrow(() -> new ResourceNotFoundException("Facture fournisseur non trouvée: " + request.getBillId()));

        if (!bill.getCompany().getId().equals(companyId)) {
            throw new ValidationException("La facture n'appartient pas à cette entreprise");
        }

        // Valider le montant
        if (request.getAmount().compareTo(bill.getAmountDue()) > 0) {
            throw new ValidationException("Le montant du paiement dépasse le montant dû");
        }

        // Générer numéro de paiement
        String paymentNumber = generatePaymentNumber();

        // Créer le paiement
        Payment payment = Payment.builder()
            .company(company)
            .paymentNumber(paymentNumber)
            .paymentType(PaymentType.SUPPLIER_PAYMENT)
            .bill(bill)
            .supplier(bill.getSupplier())
            .paymentDate(request.getPaymentDate())
            .amount(request.getAmount())
            .paymentMethod(request.getPaymentMethod())
            .bankAccountId(request.getBankAccountId())
            .chequeNumber(request.getChequeNumber())
            .mobileMoneyNumber(request.getMobileMoneyNumber())
            .transactionReference(request.getTransactionReference())
            .description(request.getDescription())
            .notes(request.getNotes())
            .status(PaymentStatus.COMPLETED)
            .build();

        payment = paymentRepository.save(payment);

        // Mettre à jour la facture fournisseur
        bill.recordPayment(request.getAmount());
        billRepository.save(bill);

        // Lettrage automatique si totalement payé
        if (bill.getStatus() == com.predykt.accounting.domain.enums.BillStatus.PAID) {
            payment.setIsReconciled(true);
            payment.setReconciliationDate(LocalDate.now());
            bill.setIsReconciled(true);
            bill.setReconciliationDate(LocalDate.now());
            paymentRepository.save(payment);
            billRepository.save(bill);
            log.info("✅ Lettrage automatique effectué pour facture {}", bill.getBillNumber());
        }

        // Générer l'écriture comptable
        GeneralLedger entry = generateSupplierPaymentEntry(payment, bill);
        payment.setGeneralLedger(entry);
        paymentRepository.save(payment);

        log.info("✅ Paiement fournisseur {} enregistré - Facture {} - Statut: {}",
            payment.getPaymentNumber(), bill.getBillNumber(), bill.getStatus());

        return toResponse(payment);
    }

    /**
     * Lister tous les paiements
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments(Long companyId, PaymentType type) {
        Company company = findCompanyOrThrow(companyId);

        List<Payment> payments;
        if (type != null) {
            payments = paymentRepository.findByCompanyAndPaymentTypeOrderByPaymentDateDesc(company, type);
        } else {
            payments = paymentRepository.findByCompanyOrderByPaymentDateDesc(company);
        }

        return payments.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Obtenir un paiement par ID
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long companyId, Long paymentId) {
        Payment payment = findPaymentByIdAndCompany(companyId, paymentId);
        return toResponse(payment);
    }

    /**
     * Annuler un paiement (si non lettré)
     */
    public PaymentResponse cancelPayment(Long companyId, Long paymentId) {
        log.info("❌ Annulation paiement {} pour entreprise {}", paymentId, companyId);

        Payment payment = findPaymentByIdAndCompany(companyId, paymentId);

        if (payment.getIsReconciled()) {
            throw new ValidationException("Impossible d'annuler un paiement lettré");
        }

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new ValidationException("Le paiement est déjà annulé");
        }

        payment.setStatus(PaymentStatus.CANCELLED);

        // Remettre le montant sur la facture
        if (payment.getInvoice() != null) {
            Invoice invoice = payment.getInvoice();
            invoice.setAmountPaid(invoice.getAmountPaid().subtract(payment.getAmount()));
            invoice.setAmountDue(invoice.getAmountDue().add(payment.getAmount()));
            invoice.setStatus(InvoiceStatus.ISSUED);
            invoiceRepository.save(invoice);
        } else if (payment.getBill() != null) {
            Bill bill = payment.getBill();
            bill.setAmountPaid(bill.getAmountPaid().subtract(payment.getAmount()));
            bill.setAmountDue(bill.getAmountDue().add(payment.getAmount()));
            bill.setStatus(com.predykt.accounting.domain.enums.BillStatus.ISSUED);
            billRepository.save(bill);
        }

        payment = paymentRepository.save(payment);

        log.info("✅ Paiement {} annulé", payment.getPaymentNumber());
        return toResponse(payment);
    }

    // ==================== Méthodes privées ====================

    /**
     * Générer l'écriture comptable pour un paiement client
     *
     * Journal BQ (Banque):
     * DÉBIT  | 521 (Banque)           | 1 192 500 | Argent reçu
     * CRÉDIT | 4111001 (Client)       | 1 192 500 | Annulation créance
     *
     * ✅ UTILISE GeneralLedgerService.recordJournalEntry() pour:
     * - Validation de la partie double (débit = crédit)
     * - Vérification du verrouillage de période
     * - Détection automatique TVA déductible
     */
    private GeneralLedger generateCustomerPaymentEntry(Payment payment, Invoice invoice) {
        log.info("🔄 Génération écriture comptable paiement client {}", payment.getPaymentNumber());

        Company company = payment.getCompany();
        String customerAccount = invoice.getCustomer().getAuxiliaryAccountNumber();

        // Construire la requête d'écriture comptable avec 2 lignes (partie double)
        List<JournalEntryLineRequest> lines = List.of(
            // Ligne 1: DÉBIT Banque (argent reçu)
            JournalEntryLineRequest.builder()
                .accountNumber(BANK_ACCOUNT_DEFAULT)
                .description("Encaissement " + invoice.getCustomer().getName() + " - " + invoice.getInvoiceNumber())
                .debitAmount(payment.getAmount())
                .creditAmount(BigDecimal.ZERO)
                .build(),

            // Ligne 2: CRÉDIT Client (annulation créance)
            JournalEntryLineRequest.builder()
                .accountNumber(customerAccount)
                .description("Règlement facture " + invoice.getInvoiceNumber())
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(payment.getAmount())
                .build()
        );

        JournalEntryRequest request = JournalEntryRequest.builder()
            .entryDate(payment.getPaymentDate())
            .journalCode("BQ")  // Journal banque
            .reference(payment.getPaymentNumber())
            .lines(lines)
            .build();

        // ✅ Utiliser GeneralLedgerService qui VALIDE la partie double
        List<GeneralLedger> entries = generalLedgerService.recordJournalEntry(company.getId(), request);

        log.info("✅ Écriture paiement client validée: DÉBIT {} / CRÉDIT {} = {} XAF",
            BANK_ACCOUNT_DEFAULT, customerAccount, payment.getAmount());

        // Retourner la première ligne (Banque) comme écriture principale
        return entries.get(0);
    }

    /**
     * Générer l'écriture comptable pour un paiement fournisseur
     *
     * Journal BQ:
     * DÉBIT  | 4011001 (Fournisseur)  | 585 250 | Annulation dette
     * CRÉDIT | 521 (Banque)           | 585 250 | Argent payé
     *
     * ✅ UTILISE GeneralLedgerService.recordJournalEntry() pour:
     * - Validation de la partie double (débit = crédit)
     * - Vérification du verrouillage de période
     * - Détection automatique TVA déductible
     */
    private GeneralLedger generateSupplierPaymentEntry(Payment payment, Bill bill) {
        log.info("🔄 Génération écriture comptable paiement fournisseur {}", payment.getPaymentNumber());

        Company company = payment.getCompany();
        String supplierAccount = bill.getSupplier().getAuxiliaryAccountNumber();

        // Construire la requête d'écriture comptable avec 2 lignes (partie double)
        List<JournalEntryLineRequest> lines = List.of(
            // Ligne 1: DÉBIT Fournisseur (annulation dette)
            JournalEntryLineRequest.builder()
                .accountNumber(supplierAccount)
                .description("Paiement facture " + bill.getBillNumber())
                .debitAmount(payment.getAmount())
                .creditAmount(BigDecimal.ZERO)
                .build(),

            // Ligne 2: CRÉDIT Banque (argent payé)
            JournalEntryLineRequest.builder()
                .accountNumber(BANK_ACCOUNT_DEFAULT)
                .description("Décaissement " + bill.getSupplier().getName() + " - " + bill.getBillNumber())
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(payment.getAmount())
                .build()
        );

        JournalEntryRequest request = JournalEntryRequest.builder()
            .entryDate(payment.getPaymentDate())
            .journalCode("BQ")  // Journal banque
            .reference(payment.getPaymentNumber())
            .lines(lines)
            .build();

        // ✅ Utiliser GeneralLedgerService qui VALIDE la partie double
        List<GeneralLedger> entries = generalLedgerService.recordJournalEntry(company.getId(), request);

        log.info("✅ Écriture paiement fournisseur validée: DÉBIT {} / CRÉDIT {} = {} XAF",
            supplierAccount, BANK_ACCOUNT_DEFAULT, payment.getAmount());

        // Retourner la première ligne (Fournisseur) comme écriture principale
        return entries.get(0);
    }

    /**
     * Générer le numéro de paiement (PAY-2025-0001)
     */
    private String generatePaymentNumber() {
        int year = LocalDate.now().getYear();
        Long sequence = jdbcTemplate.queryForObject(
            "SELECT nextval('seq_payment_number')",
            Long.class
        );
        return String.format("%s-%d-%04d", PAYMENT_PREFIX, year, sequence);
    }

    /**
     * Convertir Payment en PaymentResponse
     */
    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
            .id(payment.getId())
            .companyId(payment.getCompany().getId())
            .paymentNumber(payment.getPaymentNumber())
            .paymentType(payment.getPaymentType())
            .invoiceId(payment.getInvoice() != null ? payment.getInvoice().getId() : null)
            .invoiceNumber(payment.getInvoice() != null ? payment.getInvoice().getInvoiceNumber() : null)
            .billId(payment.getBill() != null ? payment.getBill().getId() : null)
            .billNumber(payment.getBill() != null ? payment.getBill().getBillNumber() : null)
            .customerId(payment.getCustomer() != null ? payment.getCustomer().getId() : null)
            .customerName(payment.getCustomer() != null ? payment.getCustomer().getName() : null)
            .supplierId(payment.getSupplier() != null ? payment.getSupplier().getId() : null)
            .supplierName(payment.getSupplier() != null ? payment.getSupplier().getName() : null)
            .paymentDate(payment.getPaymentDate())
            .amount(payment.getAmount())
            .paymentMethod(payment.getPaymentMethod())
            .bankAccountId(payment.getBankAccountId())
            .chequeNumber(payment.getChequeNumber())
            .mobileMoneyNumber(payment.getMobileMoneyNumber())
            .transactionReference(payment.getTransactionReference())
            .status(payment.getStatus())
            .isReconciled(payment.getIsReconciled())
            .reconciliationDate(payment.getReconciliationDate())
            .reconciledBy(payment.getReconciledBy())
            .description(payment.getDescription())
            .notes(payment.getNotes())
            .build();
    }

    private Payment findPaymentByIdAndCompany(Long companyId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Paiement non trouvé: " + paymentId));

        if (!payment.getCompany().getId().equals(companyId)) {
            throw new ValidationException("Ce paiement n'appartient pas à cette entreprise");
        }

        return payment;
    }

    private Company findCompanyOrThrow(Long companyId) {
        return companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée: " + companyId));
    }
}
