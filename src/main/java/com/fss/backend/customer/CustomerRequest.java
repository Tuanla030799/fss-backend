package com.fss.backend.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(@NotBlank String fullName, @Email String email, @NotBlank String phone, String status) {}
