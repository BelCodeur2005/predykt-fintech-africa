# 🔐 SYSTÈME D'AUTHENTIFICATION - PREDYKT Accounting API

## 📋 Table des Matières

1. [Vue d'ensemble](#vue-densemble)
2. [Architecture Actuelle](#architecture-actuelle)
3. [Flux d'Authentification](#flux-dauthentification)
4. [Composants Détaillés](#composants-détaillés)
5. [Structure de Données](#structure-de-données)
6. [État Actuel (MVP)](#état-actuel-mvp)
7. [Problèmes Identifiés](#problèmes-identifiés)
8. [Recommandations Production](#recommandations-production)
9. [Guide d'Implémentation](#guide-dimplémentation)

---

## 🎯 Vue d'Ensemble

PREDYKT implémente un système d'authentification **JWT (JSON Web Token)** avec support **multi-tenant** et contrôle d'accès basé sur les rôles (RBAC).

### Caractéristiques Principales

✅ **Authentification Stateless** : Tokens JWT (pas de session serveur)
✅ **Multi-Tenant** : Isolation par entreprise (Company) + Validation tenant
✅ **RBAC** : Rôles (Admin, Comptable, Manager, Viewer) + Permissions granulaires
✅ **Sécurité** : BCrypt (strength 12), HS512 signature, validation tenant
✅ **Audit** : Tracking sessions, IP, User-Agent
⚠️ **État MVP** : Authentification DÉSACTIVÉE en développement (`.anyRequest().permitAll()`)

---

## 🏗️ Architecture Actuelle

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT (Frontend)                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP Request
                              │ Header: Authorization: Bearer <JWT>
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SPRING SECURITY FILTER CHAIN                  │
│                                                                  │
│  1. JwtAuthenticationFilter (OncePerRequestFilter)              │
│     ├─ Extrait JWT du header Authorization                      │
│     ├─ Valide signature + expiration (JwtTokenProvider)         │
│     ├─ Valide contexte tenant (sécurité multi-tenant)           │
│     └─ Charge UserDetails (CustomUserDetailsService)            │
│                                                                  │
│  2. SecurityFilterChain (SecurityConfig)                        │
│     ├─ CORS Configuration                                       │
│     ├─ CSRF Disabled (API REST stateless)                       │
│     └─ Authorization Rules (MVP: .permitAll())                  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SPRING SECURITY CONTEXT                       │
│                                                                  │
│  SecurityContextHolder.getContext().setAuthentication(...)      │
│                                                                  │
│  ├─ Principal: User (UserDetails)                               │
│  ├─ Authorities: [ROLE_ADMIN, PERMISSION_XXX, ...]              │
│  └─ Authenticated: true                                         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         CONTROLLERS                              │
│                                                                  │
│  @PreAuthorize("hasRole('ADMIN')")  ← (Pas encore activé)       │
│  @PreAuthorize("hasPermission('WRITE_INVOICE')")                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Flux d'Authentification

### 1️⃣ **Connexion Utilisateur** (Non implémenté - TODO)

```java
POST /api/v1/auth/login
{
  "email": "comptable@entreprise.com",
  "password": "MotDePasse123!"
}

↓

AuthenticationController (À créer)
├─ AuthenticationManager.authenticate()
├─ CustomUserDetailsService.loadUserByUsername()
├─ BCrypt.matches(password, passwordHash)
└─ Si succès : JwtTokenProvider.generateToken(user)

↓

Response 200 OK
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "email": "comptable@entreprise.com",
    "firstName": "Jean",
    "lastName": "Dupont",
    "companyId": 5,
    "companyName": "Entreprise ABC",
    "roles": ["ROLE_ACCOUNTANT"]
  }
}
```

### 2️⃣ **Requête Authentifiée**

```java
GET /api/v1/companies/5/invoices
Header: Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...

↓

JwtAuthenticationFilter.doFilterInternal()
├─ 1. Extraire JWT du header
├─ 2. Valider signature + expiration
├─ 3. Valider tenant (tokenTenantId == envTenantId)
├─ 4. Charger User depuis DB
├─ 5. Créer Authentication Spring Security
└─ 6. Injecter dans SecurityContextHolder

↓

InvoiceController.getInvoices()
├─ Récupérer companyId du JWT (via TenantContext)
├─ Vérifier authorisation (si activé)
└─ Exécuter logique métier

↓

Response 200 OK avec données
```

### 3️⃣ **Refresh Token** (Non implémenté - TODO)

```java
POST /api/v1/auth/refresh
{
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}

↓

AuthenticationController
├─ Valider refresh token
├─ Vérifier qu'il n'est pas révoqué (table jwt_tokens)
├─ Générer nouveau access token
└─ Optionnel: nouveau refresh token (rotation)

↓

Response 200 OK
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "expiresIn": 86400
}
```

---

## 🧩 Composants Détaillés

### 1. **SecurityConfig** (`SecurityConfig.java`)

Configuration globale Spring Security.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable()) // API REST stateless
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ⚠️ MVP: TOUT EST PUBLIC
                .anyRequest().permitAll()

                // 🔒 PRODUCTION (à activer):
                // .requestMatchers("/health/**", "/actuator/**").permitAll()
                // .requestMatchers("/api/v1/auth/**").permitAll()
                // .anyRequest().authenticated()
            )
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Force 12 (4096 rounds)
    }
}
```

**Configuration CORS** :
- Autorise `localhost:3000` (Next.js), `localhost:8000` (Python)
- Méthodes: GET, POST, PUT, DELETE, OPTIONS
- Credentials: true (cookies)

---

### 2. **JwtTokenProvider** (`JwtTokenProvider.java`)

Génération et validation des tokens JWT.

#### Structure du Token JWT

```json
{
  "sub": "comptable@entreprise.com",        // Email (username)
  "iss": "PREDYKT",                         // Émetteur
  "aud": "entreprise.predykt.com",          // Audience (tenant domain)
  "iat": 1735200000,                        // Issued At
  "exp": 1735286400,                        // Expiration (24h)

  "userId": 42,                             // ID utilisateur
  "email": "comptable@entreprise.com",
  "companyId": 5,                           // ID entreprise (multi-tenant)
  "companyName": "Entreprise ABC",
  "tenantId": "eti-abc-001",                // ID tenant (DEDICATED mode)
  "tenantDomain": "abc.predykt.com",

  "roles": [                                // Rôles RBAC
    "ROLE_ACCOUNTANT"
  ],
  "permissions": [                          // Permissions granulaires
    "READ_INVOICE",
    "WRITE_INVOICE",
    "READ_JOURNAL",
    "WRITE_JOURNAL"
  ]
}
```

#### Méthodes Principales

```java
// Génération
public String generateToken(User user)
public String generateRefreshToken(User user)

// Extraction
public String getUsernameFromToken(String token)
public Long getUserIdFromToken(String token)
public String getTenantIdFromToken(String token)
public Object getClaim(String token, String claimName)

// Validation
public boolean validateToken(String token)              // Signature + expiration
public boolean validateTenantContext(String token)      // Sécurité tenant
```

#### Algorithme de Signature

- **Algorithme** : `HS512` (HMAC-SHA512)
- **Clé** : `spring.security.jwt.secret` (env variable `JWT_SECRET`)
- **⚠️ Problème** : Clé actuelle probablement trop courte (320 bits, min 512 bits requis)

---

### 3. **JwtAuthenticationFilter** (`JwtAuthenticationFilter.java`)

Filtre qui intercepte TOUTES les requêtes pour valider le JWT.

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {

        // 1. Extraire token du header "Authorization: Bearer <token>"
        String jwt = extractJwtFromRequest(request);

        if (jwt != null) {
            // 2. Valider signature + expiration
            if (tokenProvider.validateToken(jwt)) {

                // 3. SÉCURITÉ MULTI-TENANT: Vérifier que le token
                //    appartient bien au tenant actuel
                if (!tokenProvider.validateTenantContext(jwt)) {
                    response.setStatus(403);
                    response.getWriter().write("{\"error\":\"Token invalide pour ce tenant\"}");
                    return;
                }

                // 4. Charger User complet depuis DB
                String email = tokenProvider.getUsernameFromToken(jwt);
                UserDetails user = userDetailsService.loadUserByUsername(email);

                // 5. Créer Authentication Spring Security
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities()
                    );

                // 6. Injecter dans SecurityContext (accessible via @AuthenticationPrincipal)
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 7. Continuer la chaîne de filtres
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Ne pas filtrer ces endpoints (publics)
        return path.startsWith("/api/v1/auth/") ||
               path.startsWith("/health") ||
               path.startsWith("/swagger-ui");
    }
}
```

**Points Critiques** :
- ✅ Exécution **une seule fois** par requête (`OncePerRequestFilter`)
- ✅ Validation **tenant** empêche l'accès cross-tenant
- ✅ Chargement User depuis DB (pas juste les claims JWT)
- ⚠️ Pas de cache User (requête DB à chaque appel API)

---

### 4. **CustomUserDetailsService** (`CustomUserDetailsService.java`)

Service de chargement des utilisateurs pour Spring Security.

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé"));

        // Vérifications de sécurité
        if (!user.getIsActive()) {
            throw new UsernameNotFoundException("Compte désactivé");
        }

        if (!user.isAccountNonLocked()) {
            throw new UsernameNotFoundException("Compte verrouillé");
        }

        return user; // Implémente UserDetails
    }
}
```

**Vérifications** :
- ✅ Compte actif (`is_active = true`)
- ✅ Compte non verrouillé (`locked_until < NOW`)
- ✅ Eager loading des rôles (`@ManyToMany(fetch = EAGER)`)

---

### 5. **User Entity** (`User.java`)

Entité JPA implémentant `UserDetails` de Spring Security.

```java
@Entity
@Table(name = "users")
public class User extends BaseEntity implements UserDetails {

    private Long id;

    @ManyToOne
    private Company company;                  // Multi-tenant

    private String email;                     // Username
    private String passwordHash;              // BCrypt
    private String firstName;
    private String lastName;

    private Boolean isActive = true;
    private Boolean isEmailVerified = false;

    // Sécurité
    private String passwordResetToken;
    private LocalDateTime passwordResetExpiresAt;
    private Integer failedLoginAttempts = 0;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;

    // Relations
    @ManyToMany(fetch = EAGER)
    private Set<Role> roles = new HashSet<>();

    // ========== USERDETAILS INTERFACE ==========

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Retourne rôles + permissions combinés
        Set<GrantedAuthority> authorities = new HashSet<>();

        // Ajouter rôles (ex: ROLE_ADMIN)
        roles.forEach(role ->
            authorities.add(new SimpleGrantedAuthority(role.getName()))
        );

        // Ajouter permissions (ex: WRITE_INVOICE)
        roles.forEach(role ->
            role.getPermissions().forEach(permission ->
                authorities.add(new SimpleGrantedAuthority(permission.getPermissionCode()))
            )
        );

        return authorities;
    }

    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
    }

    @Override
    public boolean isEnabled() {
        return isActive != null && isActive;
    }
}
```

---

### 6. **Role & Permission** (RBAC)

#### Rôles par Défaut

| Rôle | Description | Permissions Typiques |
|------|-------------|----------------------|
| **ROLE_ADMIN** | Administrateur système | Tout (CRUD complet) |
| **ROLE_ACCOUNTANT** | Comptable | Écritures, rapports, TVA, immobilisations |
| **ROLE_MANAGER** | Manager | Lecture rapports, analytics, budgets |
| **ROLE_VIEWER** | Lecteur | Lecture seule (pas de modification) |

#### Structure Permissions

```java
@Entity
@Table(name = "permissions")
public class Permission {

    private Integer id;
    private String permissionCode;    // Ex: "WRITE_INVOICE"
    private String module;            // Ex: "ACCOUNTING"
    private String description;
    private Boolean isSystem = false;

    @ManyToMany(mappedBy = "permissions")
    private Set<Role> roles = new HashSet<>();
}
```

**Exemples de permissions** :
- `READ_INVOICE`, `WRITE_INVOICE`, `DELETE_INVOICE`
- `READ_JOURNAL`, `WRITE_JOURNAL`, `LOCK_PERIOD`
- `READ_REPORTS`, `EXPORT_REPORTS`
- `MANAGE_USERS`, `MANAGE_COMPANY`

---

## 💾 Structure de Données

### Tables Principales

```sql
-- Rôles système
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,  -- ROLE_ADMIN, ROLE_ACCOUNTANT...
    description TEXT,
    is_system BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Permissions granulaires
CREATE TABLE permissions (
    id SERIAL PRIMARY KEY,
    permission_code VARCHAR(100) UNIQUE NOT NULL,  -- WRITE_INVOICE
    module VARCHAR(50) NOT NULL,                   -- ACCOUNTING
    description TEXT,
    is_system BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Relation N-N: rôle ↔ permissions
CREATE TABLE role_permissions (
    role_id INTEGER REFERENCES roles(id),
    permission_id INTEGER REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

-- Utilisateurs (multi-tenant via company_id)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id),   -- MULTI-TENANT
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,          -- BCrypt
    first_name VARCHAR(100),
    last_name VARCHAR(100),

    is_active BOOLEAN DEFAULT TRUE,
    is_email_verified BOOLEAN DEFAULT FALSE,

    -- Sécurité
    password_reset_token VARCHAR(255),
    password_reset_expires_at TIMESTAMP,
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    last_login_at TIMESTAMP,

    -- Audit
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP,
    version BIGINT DEFAULT 0,

    CONSTRAINT uk_user_email_company UNIQUE(email, company_id)
);

-- Relation N-N: user ↔ roles
CREATE TABLE user_roles (
    user_id BIGINT REFERENCES users(id),
    role_id INTEGER REFERENCES roles(id),
    granted_at TIMESTAMP DEFAULT NOW(),
    granted_by VARCHAR(100),
    PRIMARY KEY (user_id, role_id)
);

-- Tokens JWT (pour révocation)
CREATE TABLE jwt_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    token_hash VARCHAR(255) UNIQUE NOT NULL,     -- SHA256(token)
    token_type VARCHAR(20) NOT NULL,             -- ACCESS, REFRESH
    expires_at TIMESTAMP NOT NULL,
    is_revoked BOOLEAN DEFAULT FALSE,
    revoked_at TIMESTAMP,
    revoked_reason VARCHAR(255),

    -- Tracking
    ip_address INET,
    user_agent VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Sessions utilisateurs (tracking)
CREATE TABLE user_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    session_token VARCHAR(255) UNIQUE NOT NULL,
    ip_address INET,
    user_agent VARCHAR(255),
    login_at TIMESTAMP DEFAULT NOW(),
    last_activity_at TIMESTAMP,
    logout_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);
```

---

## ⚠️ État Actuel (MVP)

### ✅ Ce Qui Est Implémenté

1. **Infrastructure Complète** :
   - ✅ Tables DB (users, roles, permissions, jwt_tokens)
   - ✅ Entités JPA (User, Role, Permission)
   - ✅ JwtTokenProvider (génération + validation)
   - ✅ JwtAuthenticationFilter (extraction + validation)
   - ✅ CustomUserDetailsService (chargement utilisateur)
   - ✅ BCrypt password encoder (strength 12)
   - ✅ Validation multi-tenant (token.tenantId == env.tenantId)

2. **Sécurité JWT** :
   - ✅ Signature HS512
   - ✅ Expiration 24h (access) / 7 jours (refresh)
   - ✅ Claims custom (userId, companyId, tenantId, roles, permissions)
   - ✅ Validation signature + expiration

3. **RBAC** :
   - ✅ 4 rôles par défaut (ADMIN, ACCOUNTANT, MANAGER, VIEWER)
   - ✅ Permissions granulaires
   - ✅ Relations N-N (user ↔ roles ↔ permissions)

### ❌ Ce Qui N'Est PAS Implémenté

1. **Endpoints Authentification** :
   - ❌ POST /api/v1/auth/login
   - ❌ POST /api/v1/auth/register
   - ❌ POST /api/v1/auth/refresh
   - ❌ POST /api/v1/auth/logout
   - ❌ POST /api/v1/auth/forgot-password
   - ❌ POST /api/v1/auth/reset-password

2. **Autorisation** :
   - ❌ @PreAuthorize sur les contrôleurs
   - ❌ SecurityFilterChain avec `.authenticated()`
   - ❌ Vérification permissions métier

3. **Fonctionnalités Sécurité** :
   - ❌ Révocation tokens (table jwt_tokens non utilisée)
   - ❌ Refresh token rotation
   - ❌ Limitation tentatives connexion (failed_login_attempts)
   - ❌ Email verification
   - ❌ 2FA / MFA

4. **Audit & Monitoring** :
   - ❌ Logging des connexions
   - ❌ Tracking sessions actives
   - ❌ Alertes accès suspects

### 🚨 Configuration Actuelle (DANGER)

```java
// SecurityConfig.java:44
.authorizeHttpRequests(auth -> auth
    .anyRequest().permitAll()  // ⚠️ TOUT LE MONDE A ACCÈS À TOUT
)
```

**Conséquences** :
- ⛔ **Aucune authentification requise**
- ⛔ N'importe qui peut accéder aux données
- ⛔ Pas de vérification des rôles/permissions
- ⛔ Les tokens JWT sont générés mais **jamais vérifiés**

---

## 🔴 Problèmes Identifiés

### 1. **Sécurité Critique**

| Problème | Impact | Priorité |
|----------|--------|----------|
| **Authentification désactivée** (`.permitAll()`) | 🔴 Critique | P0 |
| **Clé JWT probablement trop courte** | 🔴 Critique | P0 |
| **Pas de révocation tokens** | 🟠 Élevé | P1 |
| **Pas de rate limiting** | 🟠 Élevé | P1 |
| **CSRF désactivé** | 🟡 Moyen | P2 |
| **Pas de 2FA** | 🟡 Moyen | P2 |

### 2. **Problèmes Techniques**

#### a) Clé JWT Insuffisante

```java
// JwtTokenProvider.java:73
.signWith(getSigningKey(), SignatureAlgorithm.HS512)

// Problème: HS512 requiert 512 bits (64 caractères)
// Si JWT_SECRET < 64 chars → Exception ou sécurité réduite
```

**Solution** :
```bash
# Générer une clé sécurisée 512 bits
openssl rand -base64 64

# Ou en Java
String secretKey = io.jsonwebtoken.security.Keys.secretKeyFor(
    SignatureAlgorithm.HS512
).toString();
```

#### b) Pas de Gestion des Tokens Révoqués

```java
// Actuellement: Impossible de révoquer un token
// Un token volé reste valide jusqu'à expiration (24h)

// Solution: Vérifier table jwt_tokens à chaque requête
if (jwtTokenRepository.isRevoked(tokenHash)) {
    throw new JwtException("Token révoqué");
}
```

#### c) Chargement User à Chaque Requête

```java
// JwtAuthenticationFilter:58 - À CHAQUE requête API
UserDetails user = userDetailsService.loadUserByUsername(email);

// Problème: Requête DB pour charger User + Roles + Permissions
// Impact: ~5-10ms latence par requête

// Solution: Cache Redis
@Cacheable(value = "users", key = "#email")
public UserDetails loadUserByUsername(String email) { ... }
```

#### d) Pas de Limitation Tentatives Connexion

```java
// Actuellement: Pas de protection brute-force
// Un attaquant peut tester 1000 mots de passe/seconde

// Solution: Implémenter dans AuthenticationService
if (user.getFailedLoginAttempts() >= 5) {
    user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
    throw new AccountLockedException("Trop de tentatives");
}
```

### 3. **Problèmes Architecturaux**

#### a) Pas de Séparation Backend Auth

```
❌ ACTUEL:
Frontend → Backend Java (auth + métier)

✅ RECOMMANDÉ:
Frontend → API Gateway (auth) → Backend Java (métier)
```

#### b) Refresh Token Non Implémenté

```java
// Actuellement: Access token 24h
// Problème: Si token volé, accès pendant 24h

// Recommandé:
// - Access token: 15 min
// - Refresh token: 7 jours (rotation)
// - Révocation possible
```

---

## ✅ Recommandations Production

### Phase 1 : Sécurité Critique (P0) ⚡

1. **Activer l'Authentification**

```java
// SecurityConfig.java
.authorizeHttpRequests(auth -> auth
    // Publics
    .requestMatchers("/health/**", "/actuator/health").permitAll()
    .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/v1/auth/forgot-password").permitAll()

    // Protégés
    .anyRequest().authenticated()
)
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

2. **Générer Clé JWT Sécurisée (512 bits)**

```bash
# Dans .env ou secrets manager
JWT_SECRET=$(openssl rand -base64 64)
```

3. **Implémenter Endpoints Auth**

Créer `AuthenticationController.java` :

```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        // 1. Authentifier
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
            )
        );

        // 2. Générer tokens
        User user = (User) auth.getPrincipal();
        String accessToken = jwtTokenProvider.generateToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        // 3. Enregistrer token (pour révocation)
        jwtTokenRepository.save(JwtToken.builder()
            .user(user)
            .tokenHash(hashToken(accessToken))
            .tokenType("ACCESS")
            .expiresAt(LocalDateTime.now().plusDays(1))
            .ipAddress(getClientIP(request))
            .userAgent(request.getHeader("User-Agent"))
            .build()
        );

        // 4. Mettre à jour last_login_at
        user.setLastLoginAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        // 5. Retourner tokens
        return ResponseEntity.ok(AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(86400)
            .user(userMapper.toResponse(user))
            .build()
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String token) {
        String jwt = token.substring(7);

        // Révoquer le token
        jwtTokenRepository.revokeToken(hashToken(jwt), "USER_LOGOUT");

        return ResponseEntity.noContent().build();
    }
}
```

### Phase 2 : Sécurité Renforcée (P1) 🛡️

4. **Implémenter Révocation Tokens**

```java
// JwtAuthenticationFilter - Ajouter vérification
if (jwtTokenRepository.isRevoked(hashToken(jwt))) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.getWriter().write("{\"error\":\"Token révoqué\"}");
    return;
}
```

5. **Rate Limiting (Spring Cloud Gateway ou Bucket4j)**

```java
@Component
public class RateLimitFilter implements Filter {

    private final Bucket bucket = Bucket.builder()
        .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
        .build();

    @Override
    public void doFilter(ServletRequest request, ...) {
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429); // Too Many Requests
        }
    }
}
```

6. **Limitation Tentatives Connexion**

```java
// AuthenticationService
public void handleFailedLogin(String email) {
    User user = userRepository.findByEmail(email).orElse(null);
    if (user == null) return; // Pas de user enumeration

    user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

    if (user.getFailedLoginAttempts() >= 5) {
        user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        log.warn("🔒 Compte verrouillé: {} (5 tentatives échouées)", email);
    }

    userRepository.save(user);
}
```

7. **Cache Redis pour UserDetails**

```java
@Cacheable(value = "users", key = "#email", ttl = 300)
public UserDetails loadUserByUsername(String email) {
    return userRepository.findByEmail(email)...;
}

@CacheEvict(value = "users", key = "#user.email")
public void onUserUpdated(User user) {
    // Invalider le cache lors de la modification
}
```

### Phase 3 : Fonctionnalités Avancées (P2) 🚀

8. **2FA / MFA (Google Authenticator)**

```java
// User.java
private String totpSecret;  // Secret TOTP
private Boolean isMfaEnabled = false;

// AuthenticationService
public QRCodeResponse enableMFA(User user) {
    String secret = generateTOTPSecret();
    user.setTotpSecret(secret);

    String qrCodeUrl = generateQRCodeUrl(user.getEmail(), secret);
    return new QRCodeResponse(qrCodeUrl, secret);
}

public boolean validateMFA(User user, String code) {
    return TOTP.validate(user.getTotpSecret(), code);
}
```

9. **Audit Trail Complet**

```java
@Entity
@Table(name = "security_audit_log")
public class SecurityAuditLog {
    private Long id;
    private Long userId;
    private String event;        // LOGIN, LOGOUT, PASSWORD_CHANGE, MFA_ENABLED
    private String ipAddress;
    private String userAgent;
    private Boolean success;
    private String failureReason;
    private LocalDateTime timestamp;
}
```

10. **Refresh Token Rotation**

```java
@PostMapping("/api/v1/auth/refresh")
public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
    // 1. Valider refresh token
    if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
        throw new InvalidTokenException("Refresh token invalide");
    }

    // 2. Vérifier qu'il n'est pas révoqué
    if (jwtTokenRepository.isRevoked(hashToken(request.getRefreshToken()))) {
        throw new InvalidTokenException("Refresh token révoqué");
    }

    // 3. Révoquer l'ancien refresh token (rotation)
    jwtTokenRepository.revokeToken(hashToken(request.getRefreshToken()), "TOKEN_ROTATION");

    // 4. Générer nouveaux tokens
    Long userId = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
    User user = userRepository.findById(userId).orElseThrow();

    String newAccessToken = jwtTokenProvider.generateToken(user);
    String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);

    // 5. Enregistrer nouveaux tokens
    // ...

    return ResponseEntity.ok(new AuthResponse(newAccessToken, newRefreshToken));
}
```

---

## 📖 Guide d'Implémentation

### Étape 1 : Créer AuthenticationController

```bash
# Créer le fichier
touch src/main/java/com/predykt/accounting/controller/AuthenticationController.java
```

```java
package com.predykt.accounting.controller;

import com.predykt.accounting.dto.request.LoginRequest;
import com.predykt.accounting.dto.response.AuthResponse;
import com.predykt.accounting.security.JwtTokenProvider;
import com.predykt.accounting.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String token) {
        authenticationService.logout(token.substring(7));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authenticationService.refreshToken(request));
    }
}
```

### Étape 2 : Créer AuthenticationService

```java
package com.predykt.accounting.service;

import com.predykt.accounting.domain.entity.User;
import com.predykt.accounting.dto.request.LoginRequest;
import com.predykt.accounting.dto.response.AuthResponse;
import com.predykt.accounting.repository.UserRepository;
import com.predykt.accounting.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            // 1. Authentification Spring Security
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getEmail(),
                    request.getPassword()
                )
            );

            // 2. Récupérer utilisateur
            User user = (User) authentication.getPrincipal();

            // 3. Générer tokens
            String accessToken = jwtTokenProvider.generateToken(user);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user);

            // 4. Mettre à jour last_login_at
            user.setLastLoginAt(LocalDateTime.now());
            user.setFailedLoginAttempts(0);
            userRepository.save(user);

            // 5. Retourner response
            return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400)
                .user(UserResponse.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .companyId(user.getCompany().getId())
                    .roles(user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toList()))
                    .build())
                .build();

        } catch (BadCredentialsException e) {
            handleFailedLogin(request.getEmail());
            throw new AuthenticationException("Email ou mot de passe incorrect");
        }
    }

    private void handleFailedLogin(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

            if (user.getFailedLoginAttempts() >= 5) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            }

            userRepository.save(user);
        });
    }
}
```

### Étape 3 : Activer l'Authentification

```java
// SecurityConfig.java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            // Publics
            .requestMatchers("/health/**").permitAll()
            .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()

            // Protégés
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```

### Étape 4 : Ajouter @PreAuthorize sur Contrôleurs

```java
@RestController
@RequestMapping("/api/v1/companies/{companyId}/invoices")
@PreAuthorize("hasRole('ACCOUNTANT') or hasRole('ADMIN')")
public class InvoiceController {

    @GetMapping
    @PreAuthorize("hasPermission('READ_INVOICE')")
    public List<InvoiceResponse> getInvoices(@PathVariable Long companyId) {
        // ...
    }

    @PostMapping
    @PreAuthorize("hasPermission('WRITE_INVOICE')")
    public InvoiceResponse createInvoice(@PathVariable Long companyId, ...) {
        // ...
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteInvoice(@PathVariable Long companyId, @PathVariable Long id) {
        // ...
    }
}
```

---

## 🔒 Checklist Sécurité Production

### Avant le Déploiement

- [ ] Générer clé JWT sécurisée (512 bits minimum)
- [ ] Activer authentification (`.authenticated()`)
- [ ] Implémenter endpoints auth (login, logout, refresh)
- [ ] Activer HTTPS (TLS 1.3)
- [ ] Configurer CORS restrictif (pas de wildcard)
- [ ] Activer rate limiting (100 req/min par IP)
- [ ] Implémenter révocation tokens
- [ ] Activer limitation tentatives connexion (5 max)
- [ ] Configurer logs sécurité (auth failures, lockouts)
- [ ] Mettre en place monitoring (Grafana/Prometheus)

### Après le Déploiement

- [ ] Tester authentification (succès + échecs)
- [ ] Vérifier isolation multi-tenant
- [ ] Tester révocation tokens
- [ ] Vérifier rate limiting
- [ ] Audit logs de sécurité
- [ ] Scan vulnérabilités (OWASP ZAP)
- [ ] Penetration testing
- [ ] Documentation API (Swagger avec auth)

---

## 📚 Ressources

### Documentation Officielle

- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [JWT.io](https://jwt.io/)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)

### Outils

- [JWT Debugger](https://jwt.io/)
- [BCrypt Generator](https://bcrypt-generator.com/)
- [OWASP ZAP](https://www.zaproxy.org/) (scanner vulnérabilités)

---

## 📞 Support

Pour toute question sur le système d'authentification :

1. Consulter ce README
2. Vérifier les logs Spring Security (niveau DEBUG)
3. Tester avec Postman (collection disponible)
4. Contacter l'équipe DevOps

---

**Version** : 1.0
**Dernière mise à jour** : 2025-12-15
**Auteur** : PREDYKT Core Team
