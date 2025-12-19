#!/usr/bin/env python3
"""
Script pour remplir complètement la collection Postman PREDYKT MODE CABINET
avec TOUS les 300+ endpoints
"""

import json

# Charger la collection vide
with open('PREDYKT_MODE_CABINET_COMPLETE.postman_collection.json', 'r', encoding='utf-8') as f:
    collection = json.load(f)

# Template d'endpoint
def ep(method, name, url, desc="", body=None):
    """Créer un endpoint Postman"""
    endpoint = {
        "name": f"{method} {name}",
        "request": {
            "method": method,
            "header": [],
            "url": {
                "raw": f"{{{{base_url}}}}{url}",
                "host": ["{{base_url}}"],
                "path": url.strip('/').split('/')
            },
            "description": desc
        },
        "response": [],
        "event": [{
            "listen": "test",
            "script": {
                "exec": [
                    f"pm.test('Status code is {200 if method == 'GET' else 201 if method == 'POST' else 200}', function () {{",
                    f"    pm.response.to.have.status({200 if method == 'GET' else 201 if method == 'POST' else 200});",
                    "});",
                    "pm.test('ApiResponse structure', function () {",
                    "    var jsonData = pm.response.json();",
                    "    pm.expect(jsonData).to.have.property('success');",
                    "});"
                ],
                "type": "text/javascript"
            }
        }]
    }

    if method in ["POST", "PUT", "PATCH"]:
        endpoint["request"]["header"].append({"key": "Content-Type", "value": "application/json"})
        endpoint["request"]["body"] = {"mode": "raw", "raw": json.dumps(body if body else {}, indent=2)}

    return endpoint

print('[START] Remplissage complet de la collection...')

# 00 - Health Check
collection['item'][0]['item'] = [
    ep("GET", "Health Check", "/health", "Vérification de l'état du service")
]

# 01 - Authentication
collection['item'][1]['item'] = [
    ep("POST", "Login Admin Cabinet", "/auth/login", "Connexion admin cabinet",
       {"email": "{{admin_email}}", "password": "{{admin_password}}"}),
    ep("POST", "Register User", "/auth/register", "Enregistrer nouvel utilisateur",
       {"email": "user@test.cm", "password": "User@123", "firstName": "Jean", "lastName": "Dupont"}),
    ep("POST", "Refresh Token", "/auth/refresh", "Rafraîchir token", {"refreshToken": "{{refresh_token}}"}),
    ep("POST", "Logout", "/auth/logout", "Déconnexion"),
    ep("POST", "Request Password Reset", "/auth/password-reset/request", "Demander réinitialisation mot de passe", {"email": "user@test.cm"}),
    ep("POST", "Reset Password", "/auth/password-reset/confirm", "Réinitialiser mot de passe", {"token": "reset_token", "newPassword": "NewPass@123"}),
    ep("GET", "Validate Token", "/auth/validate", "Valider token JWT")
]

# 02 - User Management
collection['item'][2]['item'] = [
    ep("GET", "List Users", "/users", "Liste tous les utilisateurs"),
    ep("GET", "User By ID", "/users/{{user_id}}", "Détails utilisateur"),
    ep("GET", "Current User Profile", "/users/me", "Profil utilisateur connecté"),
    ep("POST", "Create User", "/users", "Créer utilisateur", {"email": "new@test.cm", "firstName": "Test", "lastName": "User"}),
    ep("PUT", "Update User", "/users/{{user_id}}", "Modifier utilisateur"),
    ep("POST", "Change Password", "/users/{{user_id}}/change-password", "Changer mot de passe", {"oldPassword": "old", "newPassword": "new"}),
    ep("PUT", "Activate User", "/users/{{user_id}}/activate", "Activer utilisateur"),
    ep("PUT", "Deactivate User", "/users/{{user_id}}/deactivate", "Désactiver utilisateur"),
    ep("DELETE", "Delete User", "/users/{{user_id}}", "Supprimer utilisateur")
]

# 03 - Companies Management
collection['item'][3]['item'] = [
    ep("GET", "List All Companies", "/companies", "Liste tous les dossiers cabinet"),
    ep("GET", "Get Company By ID", "/companies/{{company_id_A}}", "Détails entreprise"),
    ep("POST", "Create Company", "/companies", "Créer nouveau dossier",
       {"name": "Entreprise Test SARL", "legalForm": "SARL", "currency": "XAF", "accountingStandard": "OHADA"}),
    ep("PUT", "Update Company", "/companies/{{company_id_A}}", "Modifier entreprise"),
    ep("DELETE", "Deactivate Company", "/companies/{{company_id_A}}", "Désactiver entreprise")
]

# 04 - Chart of Accounts (OHADA)
collection['item'][4]['item'] = [
    ep("POST", "Initialize OHADA Chart", "/companies/{{company_id_A}}/charts/initialize", "Initialiser plan comptable OHADA"),
    ep("GET", "List All Accounts", "/companies/{{company_id_A}}/charts", "Liste tous les comptes"),
    ep("GET", "Account By Number", "/companies/{{company_id_A}}/charts/{{account_client}}", "Détails compte"),
    ep("GET", "Accounts By Type", "/companies/{{company_id_A}}/charts/type/PRODUITS", "Comptes par type"),
    ep("POST", "Create Custom Account", "/companies/{{company_id_A}}/charts", "Créer compte personnalisé",
       {"accountNumber": "701999", "accountName": "Ventes spéciales", "accountType": "PRODUITS"}),
    ep("GET", "Search Accounts", "/companies/{{company_id_A}}/charts/search?query=ventes", "Rechercher comptes"),
    ep("PUT", "Activate Account", "/companies/{{company_id_A}}/charts/{{account_id}}/activate", "Activer compte"),
    ep("PUT", "Deactivate Account", "/companies/{{company_id_A}}/charts/{{account_id}}/deactivate", "Désactiver compte")
]

# 05 - Data Import (CSV)
collection['item'][5]['item'] = [
    ep("POST", "Import Activities CSV", "/companies/{{company_id_A}}/import/activities-csv", "Import CSV activités (legacy)"),
    ep("POST", "Import Activities", "/companies/{{company_id_A}}/import/activities", "Import activités (flexible)"),
    ep("POST", "Preview Import", "/companies/{{company_id_A}}/import/activities/preview", "Prévisualiser import")
]

# 06 - Bank Transactions
collection['item'][6]['item'] = [
    ep("POST", "Import Bank Transactions", "/companies/{{company_id_A}}/bank-transactions/import", "Importer relevé bancaire"),
    ep("GET", "List Transactions", "/companies/{{company_id_A}}/bank-transactions?startDate=2025-01-01&endDate=2025-01-31", "Liste transactions"),
    ep("GET", "Unreconciled Transactions", "/companies/{{company_id_A}}/bank-transactions/unreconciled", "Transactions non rapprochées"),
    ep("POST", "Reconcile Transaction", "/companies/{{company_id_A}}/bank-transactions/{{transaction_id}}/reconcile?glEntryId={{gl_entry_id}}", "Rapprocher transaction"),
    ep("GET", "Supported Bank Formats", "/companies/{{company_id_A}}/bank-transactions/supported-formats", "Formats bancaires supportés")
]

# 07 - General Ledger
collection['item'][7]['item'] = [
    ep("POST", "Record Journal Entry", "/general-ledger", "Enregistrer écriture comptable",
       {"companyId": "{{company_id_A}}", "entryDate": "2025-01-15", "description": "Vente marchandises"}),
    ep("GET", "Account Balance", "/companies/{{company_id_A}}/general-ledger/account/{{account_client}}/balance", "Solde compte"),
    ep("GET", "Account Ledger", "/companies/{{company_id_A}}/general-ledger/account/{{account_client}}", "Grand-livre compte"),
    ep("GET", "Trial Balance", "/companies/{{company_id_A}}/general-ledger/trial-balance?asOfDate=2025-01-31", "Balance de vérification"),
    ep("POST", "Lock Accounting Period", "/companies/{{company_id_A}}/general-ledger/lock-period", "Verrouiller période")
]

# 08 - Financial Reports
collection['item'][8]['item'] = [
    ep("GET", "Balance Sheet", "/companies/{{company_id_A}}/reports/balance-sheet?asOfDate=2025-01-31", "Bilan comptable"),
    ep("GET", "Income Statement", "/companies/{{company_id_A}}/reports/income-statement?startDate=2025-01-01&endDate=2025-01-31", "Compte de résultat"),
    ep("GET", "Cash Flow Statement", "/companies/{{company_id_A}}/reports/cash-flow?startDate=2025-01-01&endDate=2025-01-31", "Tableau des flux"),
    ep("GET", "TAFIRE Report", "/companies/{{company_id_A}}/reports/tafire?asOfDate=2025-01-31", "TAFIRE")
]

# 09 - Financial Ratios
collection['item'][9]['item'] = [
    ep("POST", "Calculate Ratios", "/companies/{{company_id_A}}/ratios/calculate?startDate=2025-01-01&endDate=2025-01-31", "Calculer ratios"),
    ep("GET", "Ratios By Fiscal Year", "/companies/{{company_id_A}}/ratios/fiscal-year/2025", "Ratios année fiscale"),
    ep("GET", "Ratios History", "/companies/{{company_id_A}}/ratios/history", "Historique ratios"),
    ep("GET", "Compare Ratios", "/companies/{{company_id_A}}/ratios/compare?year1=2024&year2=2025", "Comparer ratios")
]

# 10 - Invoices Management
collection['item'][10]['item'] = [
    ep("POST", "Create Invoice", "/api/v1/companies/{{company_id_A}}/invoices", "Créer facture client",
       {"customerId": "{{customer_id}}", "invoiceDate": "2025-01-15", "dueDate": "2025-02-15"}),
    ep("GET", "Invoice By ID", "/api/v1/companies/{{company_id_A}}/invoices/{{invoice_id}}", "Détails facture"),
    ep("GET", "List Invoices", "/api/v1/companies/{{company_id_A}}/invoices", "Liste factures"),
    ep("GET", "Invoices By Customer", "/api/v1/companies/{{company_id_A}}/invoices/customer/{{customer_id}}", "Factures client"),
    ep("GET", "Overdue Invoices", "/api/v1/companies/{{company_id_A}}/invoices/overdue", "Factures impayées"),
    ep("PUT", "Update Invoice", "/api/v1/companies/{{company_id_A}}/invoices/{{invoice_id}}", "Modifier facture"),
    ep("POST", "Validate Invoice", "/api/v1/companies/{{company_id_A}}/invoices/{{invoice_id}}/validate", "Valider facture"),
    ep("POST", "Cancel Invoice", "/api/v1/companies/{{company_id_A}}/invoices/{{invoice_id}}/cancel", "Annuler facture"),
    ep("GET", "Payment History", "/api/v1/companies/{{company_id_A}}/invoices/{{invoice_id}}/payments", "Historique paiements"),
    ep("DELETE", "Delete Invoice", "/api/v1/companies/{{company_id_A}}/invoices/{{invoice_id}}", "Supprimer facture")
]

# 11 - Bills Management
collection['item'][11]['item'] = [
    ep("POST", "Create Bill", "/companies/{{company_id_A}}/bills", "Créer facture fournisseur",
       {"supplierId": "{{supplier_id}}", "billDate": "2025-01-15", "dueDate": "2025-02-15"}),
    ep("GET", "Bill By ID", "/companies/{{company_id_A}}/bills/{{bill_id}}", "Détails facture fournisseur"),
    ep("GET", "List Bills", "/companies/{{company_id_A}}/bills", "Liste factures fournisseurs"),
    ep("GET", "Bills By Supplier", "/companies/{{company_id_A}}/bills/supplier/{{supplier_id}}", "Factures par fournisseur"),
    ep("PUT", "Update Bill", "/companies/{{company_id_A}}/bills/{{bill_id}}", "Modifier facture"),
    ep("POST", "Validate Bill", "/companies/{{company_id_A}}/bills/{{bill_id}}/validate", "Valider facture"),
    ep("POST", "Cancel Bill", "/companies/{{company_id_A}}/bills/{{bill_id}}/cancel", "Annuler facture"),
    ep("GET", "Overdue Bills", "/companies/{{company_id_A}}/bills/overdue", "Factures impayées"),
    ep("DELETE", "Delete Bill", "/companies/{{company_id_A}}/bills/{{bill_id}}", "Supprimer facture")
]

# 12 - Customers Management
collection['item'][12]['item'] = [
    ep("POST", "Create Customer", "/companies/{{company_id_A}}/customers", "Créer client",
       {"name": "Client Test SARL", "email": "client@test.cm", "phone": "+237670000000"}),
    ep("GET", "List Customers", "/companies/{{company_id_A}}/customers", "Liste clients"),
    ep("GET", "Customer By ID", "/companies/{{company_id_A}}/customers/{{customer_id}}", "Détails client"),
    ep("PUT", "Update Customer", "/companies/{{company_id_A}}/customers/{{customer_id}}", "Modifier client"),
    ep("DELETE", "Delete Customer", "/companies/{{company_id_A}}/customers/{{customer_id}}", "Supprimer client"),
    ep("GET", "Customer Balance", "/companies/{{company_id_A}}/customers/{{customer_id}}/balance", "Solde client"),
    ep("GET", "Customer Ledger", "/companies/{{company_id_A}}/customers/{{customer_id}}/ledger", "Grand-livre client"),
    ep("GET", "Customer Invoices", "/companies/{{company_id_A}}/customers/{{customer_id}}/invoices", "Factures client"),
    ep("GET", "Customer Payments", "/companies/{{company_id_A}}/customers/{{customer_id}}/payments", "Paiements client"),
    ep("GET", "Search Customers", "/companies/{{company_id_A}}/customers/search?query=test", "Rechercher clients")
]

# 13 - Suppliers Management
collection['item'][13]['item'] = [
    ep("POST", "Create Supplier", "/companies/{{company_id_A}}/suppliers", "Créer fournisseur",
       {"name": "Fournisseur Test", "email": "supplier@test.cm", "phone": "+237670000001"}),
    ep("GET", "List Suppliers", "/companies/{{company_id_A}}/suppliers", "Liste fournisseurs"),
    ep("GET", "Supplier By ID", "/companies/{{company_id_A}}/suppliers/{{supplier_id}}", "Détails fournisseur"),
    ep("PUT", "Update Supplier", "/companies/{{company_id_A}}/suppliers/{{supplier_id}}", "Modifier fournisseur"),
    ep("DELETE", "Delete Supplier", "/companies/{{company_id_A}}/suppliers/{{supplier_id}}", "Supprimer fournisseur"),
    ep("GET", "Supplier Balance", "/companies/{{company_id_A}}/suppliers/{{supplier_id}}/balance", "Solde fournisseur"),
    ep("GET", "Supplier Ledger", "/companies/{{company_id_A}}/suppliers/{{supplier_id}}/ledger", "Grand-livre fournisseur"),
    ep("GET", "Supplier Bills", "/companies/{{company_id_A}}/suppliers/{{supplier_id}}/bills", "Factures fournisseur"),
    ep("GET", "Supplier Payments", "/companies/{{company_id_A}}/suppliers/{{supplier_id}}/payments", "Paiements fournisseur"),
    ep("GET", "Search Suppliers", "/companies/{{company_id_A}}/suppliers/search?query=test", "Rechercher fournisseurs"),
    ep("GET", "Active Suppliers", "/companies/{{company_id_A}}/suppliers/active", "Fournisseurs actifs"),
    ep("PUT", "Activate Supplier", "/companies/{{company_id_A}}/suppliers/{{supplier_id}}/activate", "Activer fournisseur"),
    ep("PUT", "Deactivate Supplier", "/companies/{{company_id_A}}/suppliers/{{supplier_id}}/deactivate", "Désactiver fournisseur")
]

# 14 - Payments Management
collection['item'][14]['item'] = [
    ep("POST", "Record Payment", "/companies/{{company_id_A}}/payments", "Enregistrer paiement",
       {"invoiceId": "{{invoice_id}}", "amount": 50000, "paymentDate": "2025-01-20"}),
    ep("GET", "List Payments", "/companies/{{company_id_A}}/payments", "Liste paiements"),
    ep("GET", "Payment By ID", "/companies/{{company_id_A}}/payments/{{payment_id}}", "Détails paiement"),
    ep("DELETE", "Cancel Payment", "/companies/{{company_id_A}}/payments/{{payment_id}}", "Annuler paiement"),
    ep("GET", "Payments By Period", "/companies/{{company_id_A}}/payments/period?start=2025-01-01&end=2025-01-31", "Paiements par période")
]

# 15 - Deposits Management
collection['item'][15]['item'] = [
    ep("POST", "Create Deposit", "/companies/{{company_id_A}}/deposits", "Créer acompte",
       {"customerId": "{{customer_id}}", "amount": 100000, "depositDate": "2025-01-10"}),
    ep("GET", "List Deposits", "/companies/{{company_id_A}}/deposits", "Liste acomptes"),
    ep("GET", "Deposit By ID", "/companies/{{company_id_A}}/deposits/{{deposit_id}}", "Détails acompte"),
    ep("PUT", "Update Deposit", "/companies/{{company_id_A}}/deposits/{{deposit_id}}", "Modifier acompte"),
    ep("DELETE", "Delete Deposit", "/companies/{{company_id_A}}/deposits/{{deposit_id}}", "Supprimer acompte"),
    ep("POST", "Apply Deposit To Invoice", "/companies/{{company_id_A}}/deposits/{{deposit_id}}/apply", "Appliquer acompte à facture",
       {"invoiceId": "{{invoice_id}}", "amount": 50000}),
    ep("GET", "Customer Deposits", "/companies/{{company_id_A}}/deposits/customer/{{customer_id}}", "Acomptes client"),
    ep("GET", "Unapplied Deposits", "/companies/{{company_id_A}}/deposits/unapplied", "Acomptes non affectés"),
    ep("GET", "Deposit Applications", "/companies/{{company_id_A}}/deposits/{{deposit_id}}/applications", "Affectations acompte"),
    ep("POST", "Refund Deposit", "/companies/{{company_id_A}}/deposits/{{deposit_id}}/refund", "Rembourser acompte"),
    ep("GET", "Deposits By Period", "/companies/{{company_id_A}}/deposits/period?start=2025-01-01&end=2025-01-31", "Acomptes par période"),
    ep("GET", "Deposits Summary", "/companies/{{company_id_A}}/deposits/summary", "Résumé acomptes"),
    ep("POST", "Validate Deposit", "/companies/{{company_id_A}}/deposits/{{deposit_id}}/validate", "Valider acompte"),
    ep("POST", "Cancel Deposit", "/companies/{{company_id_A}}/deposits/{{deposit_id}}/cancel", "Annuler acompte"),
    ep("GET", "Deposit Balance", "/companies/{{company_id_A}}/deposits/{{deposit_id}}/balance", "Solde acompte")
]

# 16 - Tax Management
collection['item'][16]['item'] = [
    ep("GET", "Tax Liabilities", "/companies/{{company_id_A}}/taxes/liabilities?startDate=2025-01-01&endDate=2025-01-31", "Dettes fiscales"),
    ep("GET", "Tax Summary", "/companies/{{company_id_A}}/taxes/summary?year=2025", "Résumé fiscal"),
    ep("GET", "VAT Collected", "/companies/{{company_id_A}}/taxes/vat/collected?period=2025-01", "TVA collectée"),
    ep("GET", "VAT Deductible", "/companies/{{company_id_A}}/taxes/vat/deductible?period=2025-01", "TVA déductible"),
    ep("GET", "VAT To Pay", "/companies/{{company_id_A}}/taxes/vat/to-pay?period=2025-01", "TVA à payer"),
    ep("POST", "Record Tax Payment", "/companies/{{company_id_A}}/taxes/payments", "Enregistrer paiement taxe",
       {"taxType": "VAT", "amount": 150000, "paymentDate": "2025-02-15"}),
    ep("GET", "Tax Payments History", "/companies/{{company_id_A}}/taxes/payments", "Historique paiements taxes"),
    ep("GET", "CFU Calculation", "/companies/{{company_id_A}}/taxes/cfu?year=2025", "Calcul CFU"),
    ep("GET", "CFE Calculation", "/companies/{{company_id_A}}/taxes/cfe?year=2025", "Calcul CFE"),
    ep("GET", "IR Calculation", "/companies/{{company_id_A}}/taxes/ir?year=2025", "Calcul IR"),
    ep("GET", "TCA Calculation", "/companies/{{company_id_A}}/taxes/tca?year=2025", "Calcul TCA"),
    ep("GET", "CNI Calculation", "/companies/{{company_id_A}}/taxes/cni?year=2025", "Calcul CNI")
]

# 17 - VAT Declarations
collection['item'][17]['item'] = [
    ep("POST", "Create Declaration", "/companies/{{company_id_A}}/vat-declarations", "Créer déclaration TVA",
       {"period": "2025-01", "dueDate": "2025-02-15"}),
    ep("GET", "List Declarations", "/companies/{{company_id_A}}/vat-declarations", "Liste déclarations TVA"),
    ep("GET", "Declaration By ID", "/companies/{{company_id_A}}/vat-declarations/{{declaration_id}}", "Détails déclaration"),
    ep("PUT", "Update Declaration", "/companies/{{company_id_A}}/vat-declarations/{{declaration_id}}", "Modifier déclaration"),
    ep("POST", "Submit Declaration", "/companies/{{company_id_A}}/vat-declarations/{{declaration_id}}/submit", "Soumettre déclaration"),
    ep("POST", "Generate Declaration", "/companies/{{company_id_A}}/vat-declarations/generate?period=2025-01", "Générer déclaration auto"),
    ep("GET", "Declaration PDF", "/companies/{{company_id_A}}/vat-declarations/{{declaration_id}}/pdf", "Export PDF déclaration"),
    ep("GET", "Pending Declarations", "/companies/{{company_id_A}}/vat-declarations/pending", "Déclarations en attente"),
    ep("DELETE", "Delete Declaration", "/companies/{{company_id_A}}/vat-declarations/{{declaration_id}}", "Supprimer déclaration")
]

# 18 - VAT Recoverability
collection['item'][18]['item'] = [
    ep("GET", "Check Recoverability", "/companies/{{company_id_A}}/vat-recoverability/check?glEntryId={{gl_entry_id}}", "Vérifier récupérabilité TVA"),
    ep("POST", "Apply Recoverability", "/companies/{{company_id_A}}/vat-recoverability/apply", "Appliquer règles récupérabilité",
       {"glEntryId": "{{gl_entry_id}}", "ruleId": "{{rule_id}}"}),
    ep("GET", "Recoverability Summary", "/companies/{{company_id_A}}/vat-recoverability/summary?period=2025-01", "Résumé récupérabilité"),
    ep("GET", "Non Recoverable VAT", "/companies/{{company_id_A}}/vat-recoverability/non-recoverable?period=2025-01", "TVA non récupérable"),
    ep("GET", "Partially Recoverable", "/companies/{{company_id_A}}/vat-recoverability/partial?period=2025-01", "TVA partiellement récupérable"),
    ep("GET", "Recoverability By Category", "/companies/{{company_id_A}}/vat-recoverability/by-category?period=2025-01", "Récupérabilité par catégorie"),
    ep("POST", "Recalculate Period", "/companies/{{company_id_A}}/vat-recoverability/recalculate?period=2025-01", "Recalculer période")
]

# 19 - VAT Recoverability Rules
collection['item'][19]['item'] = [
    ep("POST", "Create Rule", "/companies/{{company_id_A}}/vat-rules", "Créer règle récupérabilité",
       {"category": "VEHICULES", "recoverabilityRate": 50.0, "description": "Véhicules tourisme"}),
    ep("GET", "List Rules", "/companies/{{company_id_A}}/vat-rules", "Liste règles"),
    ep("GET", "Rule By ID", "/companies/{{company_id_A}}/vat-rules/{{rule_id}}", "Détails règle"),
    ep("PUT", "Update Rule", "/companies/{{company_id_A}}/vat-rules/{{rule_id}}", "Modifier règle"),
    ep("DELETE", "Delete Rule", "/companies/{{company_id_A}}/vat-rules/{{rule_id}}", "Supprimer règle"),
    ep("GET", "Rules By Category", "/companies/{{company_id_A}}/vat-rules/category/VEHICULES", "Règles par catégorie"),
    ep("GET", "Active Rules", "/companies/{{company_id_A}}/vat-rules/active", "Règles actives"),
    ep("PUT", "Activate Rule", "/companies/{{company_id_A}}/vat-rules/{{rule_id}}/activate", "Activer règle"),
    ep("PUT", "Deactivate Rule", "/companies/{{company_id_A}}/vat-rules/{{rule_id}}/deactivate", "Désactiver règle"),
    ep("POST", "Test Rule", "/companies/{{company_id_A}}/vat-rules/{{rule_id}}/test", "Tester règle",
       {"amount": 100000, "accountNumber": "6241"}),
    ep("GET", "Rule Application History", "/companies/{{company_id_A}}/vat-rules/{{rule_id}}/history", "Historique application"),
    ep("POST", "Bulk Import Rules", "/companies/{{company_id_A}}/vat-rules/import", "Import groupé règles"),
    ep("GET", "Export Rules", "/companies/{{company_id_A}}/vat-rules/export", "Export règles")
]

# 20 - VAT Prorata
collection['item'][20]['item'] = [
    ep("POST", "Calculate Prorata", "/companies/{{company_id_A}}/vat-prorata/calculate?year=2025", "Calculer prorata TVA"),
    ep("GET", "Prorata By Year", "/companies/{{company_id_A}}/vat-prorata/year/2025", "Prorata par année"),
    ep("GET", "Prorata History", "/companies/{{company_id_A}}/vat-prorata/history", "Historique prorata"),
    ep("PUT", "Update Prorata", "/companies/{{company_id_A}}/vat-prorata/{{prorata_id}}", "Modifier prorata"),
    ep("POST", "Apply Prorata", "/companies/{{company_id_A}}/vat-prorata/apply?year=2025", "Appliquer prorata aux écritures"),
    ep("GET", "Prorata Details", "/companies/{{company_id_A}}/vat-prorata/{{prorata_id}}/details", "Détails calcul prorata"),
    ep("GET", "Prorata Impact", "/companies/{{company_id_A}}/vat-prorata/{{prorata_id}}/impact", "Impact prorata"),
    ep("POST", "Validate Prorata", "/companies/{{company_id_A}}/vat-prorata/{{prorata_id}}/validate", "Valider prorata"),
    ep("GET", "Export Prorata", "/companies/{{company_id_A}}/vat-prorata/{{prorata_id}}/export", "Export calcul prorata")
]

# 21 - Fixed Assets
collection['item'][21]['item'] = [
    ep("POST", "Create Asset", "/companies/{{company_id_A}}/fixed-assets", "Créer immobilisation",
       {"name": "Véhicule utilitaire", "acquisitionCost": 15000000, "acquisitionDate": "2025-01-15", "assetCategory": "VEHICULES"}),
    ep("GET", "List Assets", "/companies/{{company_id_A}}/fixed-assets", "Liste immobilisations"),
    ep("GET", "Asset By ID", "/companies/{{company_id_A}}/fixed-assets/{{asset_id}}", "Détails immobilisation"),
    ep("PUT", "Update Asset", "/companies/{{company_id_A}}/fixed-assets/{{asset_id}}", "Modifier immobilisation"),
    ep("DELETE", "Dispose Asset", "/companies/{{company_id_A}}/fixed-assets/{{asset_id}}/dispose", "Céder immobilisation"),
    ep("POST", "Calculate Depreciation", "/companies/{{company_id_A}}/fixed-assets/{{asset_id}}/depreciate", "Calculer amortissement"),
    ep("GET", "Depreciation Schedule", "/companies/{{company_id_A}}/fixed-assets/{{asset_id}}/schedule", "Plan amortissement"),
    ep("GET", "Assets By Category", "/companies/{{company_id_A}}/fixed-assets/category/VEHICULES", "Immobilisations par catégorie"),
    ep("GET", "Asset Register", "/companies/{{company_id_A}}/fixed-assets/register", "Registre immobilisations"),
    ep("GET", "Depreciation Report", "/companies/{{company_id_A}}/fixed-assets/depreciation-report?year=2025", "Rapport amortissements")
]

# 22 - Budgets
collection['item'][22]['item'] = [
    ep("POST", "Create Budget", "/companies/{{company_id_A}}/budgets", "Créer budget",
       {"fiscalYear": 2025, "budgetType": "OPERATING", "totalAmount": 50000000}),
    ep("GET", "List Budgets", "/companies/{{company_id_A}}/budgets", "Liste budgets"),
    ep("GET", "Budget By ID", "/companies/{{company_id_A}}/budgets/{{budget_id}}", "Détails budget"),
    ep("PUT", "Update Budget", "/companies/{{company_id_A}}/budgets/{{budget_id}}", "Modifier budget"),
    ep("DELETE", "Delete Budget", "/companies/{{company_id_A}}/budgets/{{budget_id}}", "Supprimer budget"),
    ep("POST", "Add Budget Line", "/companies/{{company_id_A}}/budgets/{{budget_id}}/lines", "Ajouter ligne budget",
       {"accountNumber": "6011", "amount": 1000000, "description": "Achats marchandises"}),
    ep("PUT", "Update Budget Line", "/companies/{{company_id_A}}/budgets/{{budget_id}}/lines/{{line_id}}", "Modifier ligne"),
    ep("DELETE", "Delete Budget Line", "/companies/{{company_id_A}}/budgets/{{budget_id}}/lines/{{line_id}}", "Supprimer ligne"),
    ep("GET", "Budget Execution", "/companies/{{company_id_A}}/budgets/{{budget_id}}/execution", "Exécution budget"),
    ep("GET", "Budget vs Actual", "/companies/{{company_id_A}}/budgets/{{budget_id}}/vs-actual", "Budget vs Réel"),
    ep("GET", "Budget Variance", "/companies/{{company_id_A}}/budgets/{{budget_id}}/variance", "Écarts budgétaires"),
    ep("POST", "Approve Budget", "/companies/{{company_id_A}}/budgets/{{budget_id}}/approve", "Approuver budget"),
    ep("POST", "Lock Budget", "/companies/{{company_id_A}}/budgets/{{budget_id}}/lock", "Verrouiller budget"),
    ep("GET", "Budget Report", "/companies/{{company_id_A}}/budgets/{{budget_id}}/report", "Rapport budget")
]

# 23 - Treasury & Cash Flow
collection['item'][23]['item'] = [
    ep("GET", "Cash Position", "/companies/{{company_id_A}}/treasury/cash-position", "Position trésorerie"),
    ep("GET", "Cash Flow Forecast", "/companies/{{company_id_A}}/treasury/forecast?months=3", "Prévisions trésorerie"),
    ep("POST", "Record Cash Transaction", "/companies/{{company_id_A}}/treasury/transactions", "Enregistrer mouvement trésorerie",
       {"amount": 500000, "transactionDate": "2025-01-20", "type": "INFLOW"}),
    ep("GET", "Daily Cash Report", "/companies/{{company_id_A}}/treasury/daily?date=2025-01-20", "Rapport journalier trésorerie"),
    ep("GET", "Bank Accounts", "/companies/{{company_id_A}}/treasury/bank-accounts", "Comptes bancaires"),
    ep("GET", "Cash Flow Analysis", "/companies/{{company_id_A}}/treasury/analysis?startDate=2025-01-01&endDate=2025-01-31", "Analyse flux trésorerie"),
    ep("GET", "Working Capital", "/companies/{{company_id_A}}/treasury/working-capital", "Fonds de roulement")
]

# 24 - Aging Reports
collection['item'][24]['item'] = [
    ep("GET", "Customer Aging", "/companies/{{company_id_A}}/aging/customers?asOfDate=2025-01-31", "Balance âgée clients"),
    ep("GET", "Supplier Aging", "/companies/{{company_id_A}}/aging/suppliers?asOfDate=2025-01-31", "Balance âgée fournisseurs")
]

# 25 - Subledgers
collection['item'][25]['item'] = [
    ep("GET", "Customer Subledger", "/companies/{{company_id_A}}/subledgers/customers", "Auxiliaire clients"),
    ep("GET", "Supplier Subledger", "/companies/{{company_id_A}}/subledgers/suppliers", "Auxiliaire fournisseurs"),
    ep("GET", "Subledger Entry", "/companies/{{company_id_A}}/subledgers/{{subledger_id}}/entries", "Écritures auxiliaire"),
    ep("GET", "Reconcile Subledger", "/companies/{{company_id_A}}/subledgers/{{subledger_id}}/reconcile", "Rapprocher auxiliaire")
]

# 26 - Auxiliary Journals
collection['item'][26]['item'] = [
    ep("GET", "Sales Journal", "/companies/{{company_id_A}}/journals/sales?startDate=2025-01-01&endDate=2025-01-31", "Journal ventes"),
    ep("GET", "Purchase Journal", "/companies/{{company_id_A}}/journals/purchases?startDate=2025-01-01&endDate=2025-01-31", "Journal achats"),
    ep("GET", "Cash Journal", "/companies/{{company_id_A}}/journals/cash?startDate=2025-01-01&endDate=2025-01-31", "Journal caisse"),
    ep("GET", "Bank Journal", "/companies/{{company_id_A}}/journals/bank?startDate=2025-01-01&endDate=2025-01-31", "Journal banque"),
    ep("GET", "Miscellaneous Journal", "/companies/{{company_id_A}}/journals/misc?startDate=2025-01-01&endDate=2025-01-31", "Journal OD"),
    ep("GET", "All Journals Summary", "/companies/{{company_id_A}}/journals/summary", "Résumé tous journaux")
]

# 27 - Notes Annexes
collection['item'][27]['item'] = [
    ep("GET", "Generate Notes Annexes", "/companies/{{company_id_A}}/notes-annexes?fiscalYear=2025", "Générer notes annexes OHADA")
]

# 28 - Dashboard
collection['item'][28]['item'] = [
    ep("GET", "Dashboard Overview", "/companies/{{company_id_A}}/dashboard", "Vue d'ensemble tableau de bord"),
    ep("GET", "KPIs", "/companies/{{company_id_A}}/dashboard/kpis", "Indicateurs clés"),
    ep("GET", "Recent Activities", "/companies/{{company_id_A}}/dashboard/activities?limit=10", "Activités récentes")
]

# 29 - Activity Templates
collection['item'][29]['item'] = [
    ep("POST", "Create Template", "/companies/{{company_id_A}}/activity-templates", "Créer template activité",
       {"name": "Vente Export", "defaultAccountNumber": "702"}),
    ep("GET", "List Templates", "/companies/{{company_id_A}}/activity-templates", "Liste templates"),
    ep("GET", "Template By ID", "/companies/{{company_id_A}}/activity-templates/{{template_id}}", "Détails template"),
    ep("PUT", "Update Template", "/companies/{{company_id_A}}/activity-templates/{{template_id}}", "Modifier template"),
    ep("DELETE", "Delete Template", "/companies/{{company_id_A}}/activity-templates/{{template_id}}", "Supprimer template"),
    ep("GET", "Search Templates", "/companies/{{company_id_A}}/activity-templates/search?query=vente", "Rechercher templates"),
    ep("POST", "Apply Template", "/companies/{{company_id_A}}/activity-templates/{{template_id}}/apply", "Appliquer template")
]

# 30 - Activity Mappings
collection['item'][30]['item'] = [
    ep("POST", "Create Mapping", "/companies/{{company_id_A}}/activity-mappings", "Créer mapping activité",
       {"activityName": "Wholesale Sales", "accountNumber": "701"}),
    ep("GET", "List Mappings", "/companies/{{company_id_A}}/activity-mappings", "Liste mappings"),
    ep("GET", "Mapping By ID", "/companies/{{company_id_A}}/activity-mappings/{{mapping_id}}", "Détails mapping"),
    ep("PUT", "Update Mapping", "/companies/{{company_id_A}}/activity-mappings/{{mapping_id}}", "Modifier mapping"),
    ep("DELETE", "Delete Mapping", "/companies/{{company_id_A}}/activity-mappings/{{mapping_id}}", "Supprimer mapping"),
    ep("GET", "Search Mappings", "/companies/{{company_id_A}}/activity-mappings/search?activity=sales", "Rechercher mappings")
]

# 31 - Matching Metrics
collection['item'][31]['item'] = [
    ep("GET", "Overall Metrics", "/companies/{{company_id_A}}/matching-metrics/overall", "Métriques globales matching"),
    ep("GET", "Metrics By Period", "/companies/{{company_id_A}}/matching-metrics/period?startDate=2025-01-01&endDate=2025-01-31", "Métriques par période"),
    ep("GET", "Confidence Distribution", "/companies/{{company_id_A}}/matching-metrics/confidence-distribution", "Distribution confiance"),
    ep("GET", "Top Matched Accounts", "/companies/{{company_id_A}}/matching-metrics/top-accounts", "Comptes les plus matchés"),
    ep("GET", "Matching Accuracy", "/companies/{{company_id_A}}/matching-metrics/accuracy", "Précision matching"),
    ep("GET", "Matching Performance", "/companies/{{company_id_A}}/matching-metrics/performance", "Performance matching"),
    ep("GET", "Error Analysis", "/companies/{{company_id_A}}/matching-metrics/errors", "Analyse erreurs"),
    ep("POST", "Refresh Metrics", "/companies/{{company_id_A}}/matching-metrics/refresh", "Rafraîchir métriques")
]

# 32 - Bank Reconciliation Advanced
collection['item'][32]['item'] = [
    ep("POST", "Smart Match", "/companies/{{company_id_A}}/bank-reconciliation/smart-match", "Matching intelligent ML",
       {"transactionId": "{{transaction_id}}"}),
    ep("POST", "Batch Match", "/companies/{{company_id_A}}/bank-reconciliation/batch-match", "Matching par lot",
       {"startDate": "2025-01-01", "endDate": "2025-01-31"}),
    ep("GET", "Match Suggestions", "/companies/{{company_id_A}}/bank-reconciliation/suggestions?transactionId={{transaction_id}}", "Suggestions matching"),
    ep("POST", "Auto Reconcile", "/companies/{{company_id_A}}/bank-reconciliation/auto-reconcile", "Rapprochement automatique"),
    ep("GET", "Reconciliation Status", "/companies/{{company_id_A}}/bank-reconciliation/status", "Statut rapprochement"),
    ep("GET", "Unmatched Transactions", "/companies/{{company_id_A}}/bank-reconciliation/unmatched", "Transactions non rapprochées"),
    ep("GET", "Matched Transactions", "/companies/{{company_id_A}}/bank-reconciliation/matched", "Transactions rapprochées"),
    ep("POST", "Unmatch Transaction", "/companies/{{company_id_A}}/bank-reconciliation/{{reconciliation_id}}/unmatch", "Défaire rapprochement"),
    ep("GET", "Reconciliation Report", "/companies/{{company_id_A}}/bank-reconciliation/report?period=2025-01", "Rapport rapprochement"),
    ep("POST", "Create Manual Match", "/companies/{{company_id_A}}/bank-reconciliation/manual-match", "Rapprochement manuel",
       {"transactionId": "{{transaction_id}}", "glEntryId": "{{gl_entry_id}}"}),
    ep("GET", "Match History", "/companies/{{company_id_A}}/bank-reconciliation/{{transaction_id}}/history", "Historique matching"),
    ep("GET", "Reconciliation Stats", "/companies/{{company_id_A}}/bank-reconciliation/stats", "Statistiques rapprochement"),
    ep("POST", "Validate Matches", "/companies/{{company_id_A}}/bank-reconciliation/validate", "Valider rapprochements"),
    ep("GET", "Pending Validations", "/companies/{{company_id_A}}/bank-reconciliation/pending-validations", "Validations en attente"),
    ep("GET", "Match Quality Score", "/companies/{{company_id_A}}/bank-reconciliation/quality-score", "Score qualité matching"),
    ep("POST", "Export Reconciliation", "/companies/{{company_id_A}}/bank-reconciliation/export", "Export rapprochements"),
    ep("GET", "Bank Statement Summary", "/companies/{{company_id_A}}/bank-reconciliation/statement-summary", "Résumé relevé bancaire"),
    ep("POST", "Import Statement", "/companies/{{company_id_A}}/bank-reconciliation/import-statement", "Importer relevé"),
    ep("GET", "Reconciliation Rules", "/companies/{{company_id_A}}/bank-reconciliation/rules", "Règles rapprochement"),
    ep("POST", "Create Reconciliation Rule", "/companies/{{company_id_A}}/bank-reconciliation/rules", "Créer règle rapprochement",
       {"name": "Règle virement", "pattern": "VIR.*", "accountNumber": "512"})
]

# 33 - ML Models & Training
collection['item'][33]['item'] = [
    ep("GET", "List Models", "/companies/{{company_id_A}}/ml/models", "Liste modèles ML"),
    ep("POST", "Train New Model", "/companies/{{company_id_A}}/ml/models/train", "Entraîner nouveau modèle"),
    ep("POST", "Deploy Model", "/companies/{{company_id_A}}/ml/models/{{ml_model_id}}/deploy", "Déployer modèle"),
    ep("POST", "Rollback Model", "/companies/{{company_id_A}}/ml/models/{{ml_model_id}}/rollback", "Rollback modèle"),
    ep("DELETE", "Delete Model", "/companies/{{company_id_A}}/ml/models/{{ml_model_id}}", "Supprimer modèle"),
    ep("GET", "Training Stats", "/companies/{{company_id_A}}/ml/models/stats", "Stats entraînement"),
    ep("GET", "Active Model Stats", "/companies/{{company_id_A}}/ml/models/active/stats", "Stats modèle actif")
]

# 34 - ML Predictions & Feedback
collection['item'][34]['item'] = [
    ep("POST", "Submit Feedback", "/companies/{{company_id_A}}/ml/predictions/{{prediction_id}}/feedback", "Soumettre feedback",
       {"wasApplied": True, "wasCorrect": True, "notes": "Suggestion correcte"}),
    ep("GET", "Prediction History", "/companies/{{company_id_A}}/ml/predictions/history", "Historique prédictions"),
    ep("GET", "Performance Metrics", "/companies/{{company_id_A}}/ml/predictions/performance", "Métriques performance"),
    ep("GET", "Feedback Stats", "/companies/{{company_id_A}}/ml/predictions/feedback/stats?startDate=2025-01-01&endDate=2025-01-31", "Stats feedback"),
    ep("POST", "Check Drift", "/companies/{{company_id_A}}/ml/predictions/check-drift", "Détecter drift")
]

# 35 - ML Experiments
collection['item'][35]['item'] = [
    ep("POST", "Create Experiment", "/companies/{{company_id_A}}/ml/experiments", "Créer expérience ML",
       {"name": "XGBoost Hyperparameter Tuning", "description": "Test max_depth variations"}),
    ep("GET", "List Experiments", "/companies/{{company_id_A}}/ml/experiments", "Liste expériences"),
    ep("GET", "Experiment By ID", "/companies/{{company_id_A}}/ml/experiments/{{experiment_id}}", "Détails expérience"),
    ep("POST", "Start Experiment Run", "/companies/{{company_id_A}}/ml/experiments/{{experiment_id}}/runs", "Démarrer run expérience",
       {"parameters": {"max_depth": 6, "learning_rate": 0.1}}),
    ep("GET", "Experiment Runs", "/companies/{{company_id_A}}/ml/experiments/{{experiment_id}}/runs", "Liste runs expérience"),
    ep("GET", "Run Details", "/companies/{{company_id_A}}/ml/experiments/{{experiment_id}}/runs/{{run_id}}", "Détails run"),
    ep("GET", "Compare Runs", "/companies/{{company_id_A}}/ml/experiments/{{experiment_id}}/compare", "Comparer runs"),
    ep("GET", "Best Run", "/companies/{{company_id_A}}/ml/experiments/{{experiment_id}}/best-run", "Meilleur run"),
    ep("DELETE", "Delete Experiment", "/companies/{{company_id_A}}/ml/experiments/{{experiment_id}}", "Supprimer expérience"),
    ep("POST", "Export Experiment", "/companies/{{company_id_A}}/ml/experiments/{{experiment_id}}/export", "Exporter expérience"),
    ep("GET", "Experiment Metrics", "/companies/{{company_id_A}}/ml/experiments/{{experiment_id}}/metrics", "Métriques expérience"),
    ep("POST", "Clone Experiment", "/companies/{{company_id_A}}/ml/experiments/{{experiment_id}}/clone", "Cloner expérience")
]

# 36 - Exports (PDF/Excel/CSV)
collection['item'][36]['item'] = [
    ep("GET", "Export Balance Sheet PDF", "/companies/{{company_id_A}}/exports/balance-sheet/pdf?asOfDate=2025-01-31", "Export Bilan PDF"),
    ep("GET", "Export Income Statement PDF", "/companies/{{company_id_A}}/exports/income-statement/pdf?startDate=2025-01-01&endDate=2025-01-31", "Export Compte résultat PDF"),
    ep("GET", "Export Trial Balance PDF", "/companies/{{company_id_A}}/exports/trial-balance/pdf?asOfDate=2025-01-31", "Export Balance vérification PDF"),
    ep("GET", "Export General Ledger PDF", "/companies/{{company_id_A}}/exports/general-ledger/pdf?accountNumber={{account_client}}", "Export Grand-livre PDF"),
    ep("GET", "Export Chart Accounts PDF", "/companies/{{company_id_A}}/exports/chart-accounts/pdf", "Export Plan comptable PDF"),
    ep("GET", "Export Balance Sheet Excel", "/companies/{{company_id_A}}/exports/balance-sheet/excel?asOfDate=2025-01-31", "Export Bilan Excel"),
    ep("GET", "Export Income Statement Excel", "/companies/{{company_id_A}}/exports/income-statement/excel?startDate=2025-01-01&endDate=2025-01-31", "Export Compte résultat Excel"),
    ep("GET", "Export Trial Balance Excel", "/companies/{{company_id_A}}/exports/trial-balance/excel?asOfDate=2025-01-31", "Export Balance vérification Excel"),
    ep("GET", "Export General Ledger Excel", "/companies/{{company_id_A}}/exports/general-ledger/excel?accountNumber={{account_client}}", "Export Grand-livre Excel"),
    ep("GET", "Export Chart Accounts Excel", "/companies/{{company_id_A}}/exports/chart-accounts/excel", "Export Plan comptable Excel"),
    ep("GET", "Export Invoices Excel", "/companies/{{company_id_A}}/exports/invoices/excel?startDate=2025-01-01&endDate=2025-01-31", "Export Factures Excel"),
    ep("GET", "Export Bills Excel", "/companies/{{company_id_A}}/exports/bills/excel?startDate=2025-01-01&endDate=2025-01-31", "Export Factures fournisseurs Excel"),
    ep("GET", "Export Customers Excel", "/companies/{{company_id_A}}/exports/customers/excel", "Export Clients Excel"),
    ep("GET", "Export Suppliers Excel", "/companies/{{company_id_A}}/exports/suppliers/excel", "Export Fournisseurs Excel"),
    ep("GET", "Export Payments Excel", "/companies/{{company_id_A}}/exports/payments/excel?startDate=2025-01-01&endDate=2025-01-31", "Export Paiements Excel"),
    ep("GET", "Export VAT Declaration PDF", "/companies/{{company_id_A}}/exports/vat-declaration/pdf/{{declaration_id}}", "Export Déclaration TVA PDF"),
    ep("GET", "Export Fixed Assets Excel", "/companies/{{company_id_A}}/exports/fixed-assets/excel", "Export Immobilisations Excel"),
    ep("GET", "Export Budget Excel", "/companies/{{company_id_A}}/exports/budget/excel/{{budget_id}}", "Export Budget Excel"),
    ep("GET", "Export Treasury Report PDF", "/companies/{{company_id_A}}/exports/treasury/pdf?startDate=2025-01-01&endDate=2025-01-31", "Export Rapport trésorerie PDF"),
    ep("GET", "Export Aging Report PDF", "/companies/{{company_id_A}}/exports/aging/pdf?asOfDate=2025-01-31&type=customers", "Export Balance âgée PDF"),
    ep("GET", "Export Cash Flow PDF", "/companies/{{company_id_A}}/exports/cash-flow/pdf?startDate=2025-01-01&endDate=2025-01-31", "Export Tableau flux PDF"),
    ep("GET", "Export TAFIRE PDF", "/companies/{{company_id_A}}/exports/tafire/pdf?asOfDate=2025-01-31", "Export TAFIRE PDF"),
    ep("GET", "Export Ratios Excel", "/companies/{{company_id_A}}/exports/ratios/excel?fiscalYear=2025", "Export Ratios financiers Excel"),
    ep("GET", "Export Bank Transactions CSV", "/companies/{{company_id_A}}/exports/bank-transactions/csv?startDate=2025-01-01&endDate=2025-01-31", "Export Transactions bancaires CSV"),
    ep("GET", "Export General Ledger CSV", "/companies/{{company_id_A}}/exports/general-ledger/csv?startDate=2025-01-01&endDate=2025-01-31", "Export Grand-livre CSV"),
    ep("GET", "Export Chart Accounts CSV", "/companies/{{company_id_A}}/exports/chart-accounts/csv", "Export Plan comptable CSV"),
    ep("GET", "Export Customers CSV", "/companies/{{company_id_A}}/exports/customers/csv", "Export Clients CSV"),
    ep("GET", "Export Suppliers CSV", "/companies/{{company_id_A}}/exports/suppliers/csv", "Export Fournisseurs CSV"),
    ep("GET", "Export Invoices CSV", "/companies/{{company_id_A}}/exports/invoices/csv?startDate=2025-01-01&endDate=2025-01-31", "Export Factures CSV"),
    ep("GET", "Export Bills CSV", "/companies/{{company_id_A}}/exports/bills/csv?startDate=2025-01-01&endDate=2025-01-31", "Export Factures fournisseurs CSV"),
    ep("GET", "Export Journal Sales CSV", "/companies/{{company_id_A}}/exports/journals/sales/csv?startDate=2025-01-01&endDate=2025-01-31", "Export Journal ventes CSV"),
    ep("GET", "Export Journal Purchases CSV", "/companies/{{company_id_A}}/exports/journals/purchases/csv?startDate=2025-01-01&endDate=2025-01-31", "Export Journal achats CSV"),
    ep("GET", "Export Journal Bank CSV", "/companies/{{company_id_A}}/exports/journals/bank/csv?startDate=2025-01-01&endDate=2025-01-31", "Export Journal banque CSV"),
    ep("GET", "Export Journal Cash CSV", "/companies/{{company_id_A}}/exports/journals/cash/csv?startDate=2025-01-01&endDate=2025-01-31", "Export Journal caisse CSV"),
    ep("GET", "Export ML Predictions CSV", "/companies/{{company_id_A}}/exports/ml/predictions/csv?startDate=2025-01-01&endDate=2025-01-31", "Export Prédictions ML CSV"),
    ep("GET", "Export ML Performance Excel", "/companies/{{company_id_A}}/exports/ml/performance/excel", "Export Performance ML Excel"),
    ep("GET", "Export Reconciliation Report Excel", "/companies/{{company_id_A}}/exports/bank-reconciliation/excel?period=2025-01", "Export Rapport rapprochement Excel"),
    ep("GET", "Export Activity Templates CSV", "/companies/{{company_id_A}}/exports/activity-templates/csv", "Export Templates activités CSV"),
    ep("GET", "Export Activity Mappings CSV", "/companies/{{company_id_A}}/exports/activity-mappings/csv", "Export Mappings activités CSV"),
    ep("GET", "Export Matching Metrics Excel", "/companies/{{company_id_A}}/exports/matching-metrics/excel", "Export Métriques matching Excel")
]

# 37 - Cabinet Management
collection['item'][37]['item'] = [
    ep("GET", "Cabinet Profile", "/cabinet/profile", "Profil cabinet comptable"),
    ep("PUT", "Update Cabinet", "/cabinet/profile", "Modifier profil cabinet"),
    ep("GET", "Cabinet Companies", "/cabinet/companies", "Liste dossiers cabinet"),
    ep("GET", "Cabinet Statistics", "/cabinet/statistics", "Statistiques cabinet"),
    ep("GET", "Cabinet Users", "/cabinet/users", "Utilisateurs cabinet"),
    ep("POST", "Add Cabinet User", "/cabinet/users", "Ajouter utilisateur cabinet",
       {"email": "collaborator@cabinet.cm", "firstName": "Pierre", "lastName": "Martin", "role": "ACCOUNTANT"}),
    ep("DELETE", "Remove Cabinet User", "/cabinet/users/{{user_id}}", "Retirer utilisateur"),
    ep("GET", "Cabinet Billing", "/cabinet/billing", "Facturation cabinet"),
    ep("GET", "Cabinet Activity Log", "/cabinet/activity-log?startDate=2025-01-01&endDate=2025-01-31", "Journal activité cabinet"),
    ep("POST", "Create Client Company", "/cabinet/companies", "Créer dossier client",
       {"name": "Nouveau Client SARL", "legalForm": "SARL", "currency": "XAF"}),
    ep("PUT", "Archive Company", "/cabinet/companies/{{company_id_A}}/archive", "Archiver dossier"),
    ep("PUT", "Restore Company", "/cabinet/companies/{{company_id_A}}/restore", "Restaurer dossier"),
    ep("GET", "Cabinet Dashboard", "/cabinet/dashboard", "Tableau bord cabinet"),
    ep("GET", "Cabinet Settings", "/cabinet/settings", "Paramètres cabinet"),
    ep("PUT", "Update Cabinet Settings", "/cabinet/settings", "Modifier paramètres cabinet")
]

# 38 - Time Tracking Cabinet
collection['item'][38]['item'] = [
    ep("POST", "Start Time Entry", "/cabinet/time-tracking/start", "Démarrer compteur temps",
       {"companyId": "{{company_id_A}}", "taskType": "ACCOUNTING", "description": "Saisie écritures"}),
    ep("POST", "Stop Time Entry", "/cabinet/time-tracking/stop/{{entry_id}}", "Arrêter compteur temps"),
    ep("GET", "Time Entries", "/cabinet/time-tracking/entries?startDate=2025-01-01&endDate=2025-01-31", "Liste temps saisis"),
    ep("GET", "Time By Company", "/cabinet/time-tracking/by-company/{{company_id_A}}?month=2025-01", "Temps par dossier"),
    ep("GET", "Time By User", "/cabinet/time-tracking/by-user/{{user_id}}?month=2025-01", "Temps par utilisateur"),
    ep("PUT", "Update Time Entry", "/cabinet/time-tracking/entries/{{entry_id}}", "Modifier temps saisi"),
    ep("DELETE", "Delete Time Entry", "/cabinet/time-tracking/entries/{{entry_id}}", "Supprimer temps saisi"),
    ep("GET", "Time Summary", "/cabinet/time-tracking/summary?month=2025-01", "Résumé temps")
]

# 39 - Cabinet Invoices
collection['item'][39]['item'] = [
    ep("POST", "Create Cabinet Invoice", "/cabinet/invoices", "Créer facture honoraires",
       {"companyId": "{{company_id_A}}", "period": "2025-01", "amount": 500000}),
    ep("GET", "Cabinet Invoices", "/cabinet/invoices", "Liste factures honoraires"),
    ep("GET", "Cabinet Invoice By ID", "/cabinet/invoices/{{invoice_id}}", "Détails facture honoraires"),
    ep("PUT", "Update Cabinet Invoice", "/cabinet/invoices/{{invoice_id}}", "Modifier facture"),
    ep("POST", "Send Cabinet Invoice", "/cabinet/invoices/{{invoice_id}}/send", "Envoyer facture"),
    ep("POST", "Mark Invoice Paid", "/cabinet/invoices/{{invoice_id}}/mark-paid", "Marquer payée"),
    ep("GET", "Unpaid Cabinet Invoices", "/cabinet/invoices/unpaid", "Factures impayées"),
    ep("GET", "Cabinet Invoice PDF", "/cabinet/invoices/{{invoice_id}}/pdf", "Export facture PDF"),
    ep("DELETE", "Delete Cabinet Invoice", "/cabinet/invoices/{{invoice_id}}", "Supprimer facture")
]

# 40 - User Company Access
collection['item'][40]['item'] = [
    ep("POST", "Grant Access", "/users/{{user_id}}/companies/{{company_id_A}}/grant", "Donner accès dossier",
       {"role": "ACCOUNTANT"}),
    ep("DELETE", "Revoke Access", "/users/{{user_id}}/companies/{{company_id_A}}/revoke", "Retirer accès"),
    ep("GET", "User Companies", "/users/{{user_id}}/companies", "Dossiers utilisateur"),
    ep("GET", "Company Users", "/companies/{{company_id_A}}/users", "Utilisateurs dossier"),
    ep("PUT", "Update User Role", "/users/{{user_id}}/companies/{{company_id_A}}/role", "Modifier rôle",
       {"role": "VIEWER"}),
    ep("GET", "User Permissions", "/users/{{user_id}}/companies/{{company_id_A}}/permissions", "Permissions utilisateur"),
    ep("GET", "Access History", "/companies/{{company_id_A}}/access-history", "Historique accès")
]

# Sauvegarder
with open('PREDYKT_MODE_CABINET_COMPLETE.postman_collection.json', 'w', encoding='utf-8') as f:
    json.dump(collection, f, indent=2, ensure_ascii=False)

total_endpoints = sum(len(folder['item']) for folder in collection['item'])
print(f'[OK] Collection complète - {total_endpoints} endpoints ajoutés')
print(f'[INFO] Fichier: PREDYKT_MODE_CABINET_COMPLETE.postman_collection.json')
