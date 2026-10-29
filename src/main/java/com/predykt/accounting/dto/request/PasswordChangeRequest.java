// ============================================
// PasswordChangeRequest.java
// ============================================
package com.predykt.accounting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class PasswordChangeRequest {
    
    @NotBlank(message = "L'ancien mot de passe est obligatoire")
    @ToString.Exclude
    private String oldPassword;
    
    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    @ToString.Exclude
    private String newPassword;
}