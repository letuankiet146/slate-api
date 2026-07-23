package com.slatevn.service;

import com.slatevn.dto.RegisterRequest;
import com.slatevn.dto.RegisterResponse;
import com.slatevn.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        throw new BadRequestException("Registration is only available via Google Sign-In");
    }
}
