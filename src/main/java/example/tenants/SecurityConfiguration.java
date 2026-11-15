package example.tenants;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@Profile("app")
public class SecurityConfiguration {
    @Bean
    UserDetailsService users(
            @Value("${TENANT_A_PASSWORD}") String passwordA,
            @Value("${TENANT_B_PASSWORD}") String passwordB) {
        if (passwordA.length() < 16 || passwordB.length() < 16 || passwordA.equals(passwordB)) {
            throw new IllegalArgumentException("Use distinct passwords of at least 16 characters");
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        // ponytail: two demonstration users; use your verified login system for a real application.
        return new InMemoryUserDetailsManager(
            User.withUsername("alice").password("{bcrypt}" + encoder.encode(passwordA))
                .authorities("tenant:tenant-a").build(),
            User.withUsername("bob").password("{bcrypt}" + encoder.encode(passwordB))
                .authorities("tenant:tenant-b").build());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Keep browser request-forgery protection enabled. The test script fetches a token.
            // The token lives in a cookie so no server session is created.
            .csrf(csrf -> csrf.csrfTokenRepository(new CookieCsrfTokenRepository()))
            .authorizeHttpRequests(requests -> requests.requestMatchers("/health").permitAll()
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .build();
    }
}
