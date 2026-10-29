import json
import os

COLLECTION = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'PREDYKT_MODE_CABINET_COMPLETE.postman_collection.json')

# Charger la collection vide
with open(COLLECTION, 'r', encoding='utf-8') as f:
    collection = json.load(f)

# Template d'endpoint de base
def create_endpoint(method, name, url, description="", body=None):
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
            "description": description
        },
        "response": [],
        "event": [{
            "listen": "test",
            "script": {
                "exec": [
                    f"pm.test('Status code is {200 if method == 'GET' else 201 if method == 'POST' else 200}', function () {{",
                    f"    pm.response.to.have.status({200 if method == 'GET' else 201 if method == 'POST' else 200});",
                    "});",
                    "",
                    "pm.test('Response has ApiResponse structure', function () {",
                    "    var jsonData = pm.response.json();",
                    "    pm.expect(jsonData).to.have.property('success');",
                    "    pm.expect(jsonData).to.have.property('data');",
                    "});"
                ],
                "type": "text/javascript"
            }
        }]
    }
    
    if method in ["POST", "PUT", "PATCH"]:
        endpoint["request"]["header"].append({
            "key": "Content-Type",
            "value": "application/json"
        })
        endpoint["request"]["body"] = {
            "mode": "raw",
            "raw": json.dumps(body if body else {}, indent=2)
        }
    
    return endpoint

print('[INFO] Remplissage des dossiers...')

# 00 - Health Check
collection['item'][0]['item'] = [
    create_endpoint("GET", "Health Check", "/health", "Vérification de l'état du service")
]

# 01 - Authentication  
collection['item'][1]['item'] = [
    create_endpoint("POST", "Login Admin Cabinet", "/auth/login", "Connexion admin cabinet", 
                   {"email": "{{admin_email}}", "password": "{{admin_password}}"}),
    create_endpoint("POST", "Register User", "/auth/register", "Enregistrer un nouvel utilisateur",
                   {"email": "user@test.cm", "password": "User@123", "firstName": "Jean", "lastName": "Dupont"}),
    create_endpoint("POST", "Refresh Token", "/auth/refresh", "Rafraîchir le token JWT",
                   {"refreshToken": "{{refresh_token}}"})
]

# 03 - Companies Management
collection['item'][3]['item'] = [
    create_endpoint("GET", "List All Companies", "/companies", "Liste tous les dossiers du cabinet"),
    create_endpoint("GET", "Get Company By ID", "/companies/{{company_id_A}}", "Détails d'une entreprise"),
    create_endpoint("POST", "Create Company", "/companies", "Créer un nouveau dossier",
                   {"name": "Entreprise Test SARL", "legalForm": "SARL", "currency": "XAF"}),
    create_endpoint("PUT", "Update Company", "/companies/{{company_id_A}}", "Modifier une entreprise"),
    create_endpoint("DELETE", "Delete Company", "/companies/{{company_id_A}}", "Désactiver une entreprise")
]

# Sauvegarder
with open(COLLECTION, 'w', encoding='utf-8') as f:
    json.dump(collection, f, indent=2, ensure_ascii=False)

print(f'[OK] Collection remplie - {sum(len(folder["item"]) for folder in collection["item"])} endpoints ajoutés')
