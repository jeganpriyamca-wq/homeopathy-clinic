package com.homeopathy.clinic.auth;
import com.homeopathy.clinic.security.JwtService; import com.homeopathy.clinic.user.*;
import org.springframework.security.authentication.BadCredentialsException; import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
@Service public class AuthService {
 private final UserRepository repo; private final PasswordEncoder pe; private final JwtService jwt;
 public AuthService(UserRepository r,PasswordEncoder p,JwtService j){repo=r;pe=p;jwt=j;}
 public LoginResponse login(LoginRequest q){User u=repo.findByEmailIgnoreCase(q.email()).orElseThrow(()->new BadCredentialsException("Invalid email or password"));
 if(!u.isActive()||!pe.matches(q.password(),u.getPassword())) throw new BadCredentialsException("Invalid email or password");
 return new LoginResponse(jwt.generateToken(u),"Bearer",u.getId(),u.getFirstName(),u.getLastName(),u.getRole().name());}
}
