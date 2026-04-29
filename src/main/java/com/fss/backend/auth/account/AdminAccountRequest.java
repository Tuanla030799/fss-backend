package com.fss.backend.auth.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAccountRequest(@NotBlank String name, @Email @NotBlank String email,
                                  @Size(min = 6) String password, String role, String status) {}
