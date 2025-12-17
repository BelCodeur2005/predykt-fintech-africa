// ============================================
// RegisterRequest.java
// ============================================
package com.predykt.accounting.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    // MODE SHARED/DEDICATED: companyId obligatoire
    // MODE CABINET: companyId ET/OU cabinetId
    private Long companyId;

    // MODE CABINET: cabinetId optionnel
    // Si fourni seul (sans companyId) → Admin cabinet sans dossier spécifique
    // Si fourni avec companyId → Utilisateur affecté à un dossier d'un cabinet
    private Long cabinetId;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format email invalide")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    private String phone;
}