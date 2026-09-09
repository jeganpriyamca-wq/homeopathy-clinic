package com.homeopathy.clinic.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService s;

  public AuthController(AuthService s) {
    this.s = s;
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest r) {
    return ResponseEntity.ok(s.login(r));
  }
}
