package com.homeopathy.clinic.security;
import com.nimbusds.jose.jwk.source.ImmutableSecret; import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*; import org.springframework.security.oauth2.jwt.*;
import javax.crypto.SecretKey; import javax.crypto.spec.SecretKeySpec; import java.nio.charset.StandardCharsets;
@Configuration public class JwtConfig {
 @Value("${app.jwt.secret}") String secret;
 @Bean SecretKey jwtSecretKey(){return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256");}
 @Bean JwtEncoder jwtEncoder(SecretKey k){return new NimbusJwtEncoder(new ImmutableSecret<>(k));}
 @Bean JwtDecoder jwtDecoder(SecretKey k){return NimbusJwtDecoder.withSecretKey(k).build();}
}
