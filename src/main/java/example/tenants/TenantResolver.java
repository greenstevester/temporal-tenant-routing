package example.tenants;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class TenantResolver {
    public String resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Authentication required");
        }
        List<String> tenants = authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .filter(authority -> authority.startsWith("tenant:"))
            .map(authority -> authority.substring("tenant:".length())).distinct().toList();
        if (tenants.size() != 1
                || !(tenants.getFirst().equals("tenant-a") || tenants.getFirst().equals("tenant-b"))) {
            throw new AccessDeniedException("Exactly one permitted tenant is required");
        }
        return tenants.getFirst();
    }
}
