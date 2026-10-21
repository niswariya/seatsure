package com.seatsure.user;

import com.seatsure.common.ConflictException;
import com.seatsure.user.AuthDtos.AuthResponse;
import com.seatsure.user.AuthDtos.LoginRequest;
import com.seatsure.user.AuthDtos.RegisterRequest;
import com.seatsure.user.AuthDtos.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = users.save(new User(req.email().toLowerCase(),
                passwordEncoder.encode(req.password()), req.fullName(), Role.USER));
        return new AuthResponse(tokens.issue(user), tokens.ttlSeconds(), UserView.of(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                // Same message for unknown email and wrong password: don't leak which accounts exist.
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return new AuthResponse(tokens.issue(user), tokens.ttlSeconds(), UserView.of(user));
    }
}
