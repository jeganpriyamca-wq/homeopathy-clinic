package com.homeopathy.clinic.config;
import com.homeopathy.clinic.user.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*; import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration public class InitialAdminConfig {
 @Bean CommandLineRunner createInitialAdmin(UserRepository r,PasswordEncoder p,@Value("${app.initial-admin.email}") String e,@Value("${app.initial-admin.password}") String pw){
 return args->{if(!r.existsByEmailIgnoreCase(e)){User u=new User();u.setFirstName("Clinic");u.setLastName("Administrator");u.setEmail(e.trim().toLowerCase());
 u.setPassword(p.encode(pw));u.setRole(Role.ADMIN);u.setActive(true);r.save(u);System.out.println("Initial ADMIN account created.");}};}
}
