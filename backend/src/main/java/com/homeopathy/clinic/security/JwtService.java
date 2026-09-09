package com.homeopathy.clinic.security;

import com.homeopathy.clinic.user.User;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

@Service
public class JwtService {
  private final JwtEncoder encoder;

  @Value("${app.jwt.expiration-minutes:30}")
  long mins;

  public JwtService(JwtEncoder e) {
    encoder = e;
  }

  public String generateToken(User u) {
    Instant n = Instant.now();
    JwtClaimsSet c =
        JwtClaimsSet.builder()
            .issuer("homeopathy-clinic")
            .issuedAt(n)
            .expiresAt(n.plus(mins, ChronoUnit.MINUTES))
            .subject(u.getEmail())
            .claim("userId", u.getId())
            .claim("role", u.getRole().name())
            .claim("firstName", u.getFirstName())
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, c)).getTokenValue();
  }
}
