package com.predykt.accounting.service;

import com.predykt.accounting.domain.entity.Cabinet;
import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.Role;
import com.predykt.accounting.domain.entity.User;
import com.predykt.accounting.dto.request.LoginRequest;
import com.predykt.accounting.dto.request.RegisterRequest;
import com.predykt.accounting.dto.response.AuthResponse;
import com.predykt.accounting.exception.AccountingException;
import com.predykt.accounting.repository.CabinetRepository;
import com.predykt.accounting.repository.CompanyRepository;
import com.predykt.accounting.repository.RoleRepository;
import com.predykt.accounting.repository.UserRepository;
import com.predykt.accounting.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Service d'authentification - Login, Register, JWT
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {
    
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final CabinetRepository cabinetRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    
    /**
     * Authentification (Login)
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("🔐 Tentative de connexion: {}", request.getEmail());
        
        try {
            // Récupérer l'utilisateur pour gérer les tentatives échouées
            User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Email ou mot de passe incorrect"));
            
            // Vérifier si le compte est verrouillé
            if (!user.isAccountNonLocked()) {
                log.warn("🔒 Tentative de connexion sur compte verrouillé: {}", request.getEmail());
                throw new AccountingException("Compte temporairement verrouillé suite à plusieurs tentatives échouées");
            }
            
            // Authentifier via Spring Security
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getEmail(),
                    request.getPassword()
                )
            );
            
            // Succès: réinitialiser les tentatives échouées
            user.recordSuccessfulLogin();
            userRepository.save(user);
            
            // Injecter l'authentification dans le contexte
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Générer les tokens JWT
            String accessToken = tokenProvider.generateToken(authentication);
            String refreshToken = tokenProvider.generateRefreshToken(user);
            
            log.info("✅ Connexion réussie: {}", request.getEmail());
            
            return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L) // 24h en secondes
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(user.getRoles().stream().map(Role::getName).toList())
                .build();
            
        } catch (BadCredentialsException e) {
            // Échec: incrémenter les tentatives échouées
            userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
                user.incrementFailedLoginAttempts();
                userRepository.save(user);
                
                if (user.getFailedLoginAttempts() >= 5) {
                    log.warn("⚠️ Compte verrouillé après 5 tentatives: {}", request.getEmail());
                }
            });
            
            log.warn("❌ Échec de connexion: {}", request.getEmail());
            throw new BadCredentialsException("Email ou mot de passe incorrect");
        }
    }
    
    /**
     * Inscription d'un nouvel utilisateur
     * Supporte MODE SHARED/DEDICATED (companyId obligatoire) et MODE CABINET (cabinetId optionnel)
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("📝 Tentative d'inscription: {}", request.getEmail());

        // Validation: au moins companyId OU cabinetId doit être fourni
        if (request.getCompanyId() == null && request.getCabinetId() == null) {
            throw new AccountingException("CompanyId ou CabinetId doit être fourni");
        }

        // Vérifier si l'email existe déjà
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("⚠️ Email déjà utilisé: {}", request.getEmail());
            throw new AccountingException("Un compte avec cet email existe déjà");
        }

        // Récupérer la company (si fournie)
        Company company = null;
        if (request.getCompanyId() != null) {
            company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new AccountingException("Entreprise non trouvée"));
        }

        // Récupérer le cabinet (si fourni)
        Cabinet cabinet = null;
        if (request.getCabinetId() != null) {
            cabinet = cabinetRepository.findById(request.getCabinetId())
                .orElseThrow(() -> new AccountingException("Cabinet non trouvé"));
        }

        // Déterminer le rôle par défaut selon le contexte
        String defaultRoleName;
        if (cabinet != null && company == null) {
            // Admin cabinet sans dossier spécifique
            defaultRoleName = "ROLE_ADMIN_CABINET";
        } else if (cabinet != null && company != null) {
            // Collaborateur affecté à un dossier
            defaultRoleName = "ROLE_COLLABORATOR";
        } else {
            // MODE SHARED/DEDICATED
            defaultRoleName = "ROLE_ACCOUNTANT";
        }

        Role defaultRole = roleRepository.findByName(defaultRoleName)
            .orElseThrow(() -> new AccountingException("Rôle par défaut non trouvé: " + defaultRoleName));

        // Créer l'utilisateur
        User user = User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .phone(request.getPhone())
            .company(company)
            .cabinet(cabinet)
            .isActive(true)
            .isEmailVerified(false) // À vérifier par email
            .roles(Set.of(defaultRole))
            .build();

        user = userRepository.save(user);

        log.info("✅ Utilisateur créé: {} (ID: {}) - CompanyId: {} - CabinetId: {}",
                user.getEmail(), user.getId(),
                company != null ? company.getId() : "N/A",
                cabinet != null ? cabinet.getId() : "N/A");

        // TODO: Envoyer email de vérification

        // Générer les tokens JWT
        String accessToken = tokenProvider.generateToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(86400L)
            .userId(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .roles(user.getRoles().stream().map(Role::getName).toList())
            .build();
    }
    
    /**
     * Rafraîchir le token d'accès avec un refresh token
     */
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String refreshToken) {
        log.debug("🔄 Rafraîchissement du token");
        
        // Valider le refresh token
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new AccountingException("Refresh token invalide ou expiré");
        }
        
        // Extraire l'email
        String email = tokenProvider.getUsernameFromToken(refreshToken);
        
        // Charger l'utilisateur
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new AccountingException("Utilisateur non trouvé"));
        
        // Générer nouveau access token
        String newAccessToken = tokenProvider.generateToken(user);
        
        log.info("✅ Token rafraîchi pour: {}", email);
        
        return AuthResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(refreshToken) // Garder le même refresh token
            .tokenType("Bearer")
            .expiresIn(86400L)
            .userId(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .roles(user.getRoles().stream().map(Role::getName).toList())
            .build();
    }
    
    /**
     * Déconnexion (révocation du token)
     */
    @Transactional
    public void logout(String token) {
        log.info("🚪 Déconnexion");
        
        // TODO: Ajouter le token à une blacklist Redis
        // Pour l'instant, la déconnexion est côté client (suppression du token)
        
        SecurityContextHolder.clearContext();
    }
    
    /**
     * Réinitialisation du mot de passe (étape 1: demande)
     */
    @Transactional
    public void requestPasswordReset(String email) {
        log.info("🔑 Demande de réinitialisation mot de passe: {}", email);
        
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new AccountingException("Utilisateur non trouvé"));
        
        // Générer un token de réinitialisation
        String resetToken = java.util.UUID.randomUUID().toString();
        
        user.setPasswordResetToken(resetToken);
        user.setPasswordResetExpiresAt(java.time.LocalDateTime.now().plusHours(24));
        
        userRepository.save(user);
        
        // TODO: Envoyer email avec le lien de réinitialisation
        log.info("📧 Email de réinitialisation envoyé à: {}", email);
    }
    
    /**
     * Réinitialisation du mot de passe (étape 2: confirmation)
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        log.info("🔑 Réinitialisation mot de passe avec token");
        
        User user = userRepository.findByPasswordResetToken(token, java.time.LocalDateTime.now())
            .orElseThrow(() -> new AccountingException("Token de réinitialisation invalide ou expiré"));
        
        // Changer le mot de passe
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        user.resetFailedLoginAttempts();
        
        userRepository.save(user);
        
        log.info("✅ Mot de passe réinitialisé pour: {}", user.getEmail());
    }
}