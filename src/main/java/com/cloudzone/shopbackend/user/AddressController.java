package com.cloudzone.shopbackend.user;

import com.cloudzone.shopbackend.common.security.AuthPrincipal;
import com.cloudzone.shopbackend.common.security.JwtService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's address book, owner-scoped.
 *
 * <p>Ownership always comes from the verified JWT, never from the URL — the same contract
 * {@code WishlistController} documents. {@code /api/addresses} is not in the public list,
 * so the resource-server filter enforces an access token on every route.
 */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;
    private final JwtService jwtService;

    public AddressController(AddressService addressService, JwtService jwtService) {
        this.addressService = addressService;
        this.jwtService = jwtService;
    }

    /** Default first, then newest. */
    @GetMapping
    public List<AddressResponse> list(JwtAuthenticationToken authentication) {
        return addressService.list(userId(authentication));
    }

    /** A single owned address; foreign ids are a 404. */
    @GetMapping("/{id}")
    public AddressResponse get(@PathVariable Long id, JwtAuthenticationToken authentication) {
        return addressService.get(userId(authentication), id);
    }

    /** Creates an address; the first one becomes the default. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(
            @Valid @RequestBody AddressCreateRequest request,
            JwtAuthenticationToken authentication) {
        return addressService.create(userId(authentication), request);
    }

    /** Partially updates an owned address; {@code null} fields are left alone. */
    @PatchMapping("/{id}")
    public AddressResponse update(
            @PathVariable Long id,
            @Valid @RequestBody AddressUpdateRequest patch,
            JwtAuthenticationToken authentication) {
        return addressService.update(userId(authentication), id, patch);
    }

    /** Marks one address as the shipping default, demoting the rest. */
    @PutMapping("/{id}/default")
    public AddressResponse setDefault(@PathVariable Long id, JwtAuthenticationToken authentication) {
        return addressService.setDefault(userId(authentication), id);
    }

    /** Deletes an owned address; deleting the default promotes the next most recent. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, JwtAuthenticationToken authentication) {
        addressService.delete(userId(authentication), id);
    }

    private long userId(JwtAuthenticationToken authentication) {
        AuthPrincipal principal = jwtService.toPrincipal(authentication.getToken());
        return principal.userId();
    }
}
