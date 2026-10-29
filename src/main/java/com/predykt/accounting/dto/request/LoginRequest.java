// ============================================
// LoginRequest.java
// ============================================
package com.predykt.accounting.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class LoginRequest {
    
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format email invalide")
    private String email;
    
    @NotBlank(message = "Le mot de passe est obligatoire")
    @ToString.Exclude
    private String password;
}
