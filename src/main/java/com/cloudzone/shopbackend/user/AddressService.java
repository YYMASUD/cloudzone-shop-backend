package com.cloudzone.shopbackend.user;

import com.cloudzone.shopbackend.common.error.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caller's address book.
 *
 * <p>Every read and write is scoped by {@code user_id} taken from the verified JWT — never
 * from a path parameter — so a foreign address id is indistinguishable from a missing one
 * (404, not 403; ids stay unenumerable).
 *
 * <p><strong>At most one default.</strong> Promoting an address clears the flag on the
 * user's others first. The very first address a user saves becomes the default
 * automatically, because a checkout flow with addresses but no default is a dead end.
 */
@Service
public class AddressService {

    /** Hard ceiling on a user's address book; a real customer has a handful. */
    private static final int MAX_ADDRESSES_PER_USER = 20;

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    /** The caller's addresses, default first then newest. */
    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long userId) {
        return addressRepository.findAllByUserIdOrderByIsDefaultDescIdDesc(userId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    /** One address owned by the caller; anything else is a 404. */
    @Transactional(readOnly = true)
    public AddressResponse get(Long userId, Long addressId) {
        return AddressResponse.from(owned(userId, addressId));
    }

    /**
     * Saves a new address. The first one becomes the default regardless of the flag
     * supplied; a {@code true} flag on a later one demotes the previous default.
     */
    @Transactional
    public AddressResponse create(Long userId, AddressCreateRequest request) {
        long existing = addressRepository.countByUserId(userId);
        if (existing >= MAX_ADDRESSES_PER_USER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "address_limit_reached",
                    "An account may hold at most %d addresses".formatted(MAX_ADDRESSES_PER_USER));
        }
        boolean wantsDefault = existing == 0 || Boolean.TRUE.equals(request.isDefault());
        if (wantsDefault) {
            addressRepository.clearDefaultFor(userId);
        }

        Address address = Address.builder()
                .user(userRepository.getReferenceById(userId))
                .label(request.label())
                .recipientName(request.recipientName().trim())
                .line1(request.line1().trim())
                .line2(request.line2())
                .city(request.city().trim())
                .state(request.state())
                .postalCode(request.postalCode())
                .country(request.country().toUpperCase())
                .isDefault(wantsDefault)
                .build();
        return AddressResponse.from(addressRepository.save(address));
    }

    /**
     * Applies the non-null fields of the patch. Setting {@code isDefault=false} on the
     * current default is honoured, but create/delete keep the "a user with addresses has
     * a default" invariant for every other path.
     */
    @Transactional
    public AddressResponse update(Long userId, Long addressId, AddressUpdateRequest patch) {
        Address address = owned(userId, addressId);

        if (patch.label() != null) {
            address.setLabel(patch.label());
        }
        if (patch.recipientName() != null) {
            address.setRecipientName(patch.recipientName().trim());
        }
        if (patch.line1() != null) {
            address.setLine1(patch.line1().trim());
        }
        if (patch.line2() != null) {
            address.setLine2(patch.line2());
        }
        if (patch.city() != null) {
            address.setCity(patch.city().trim());
        }
        if (patch.state() != null) {
            address.setState(patch.state());
        }
        if (patch.postalCode() != null) {
            address.setPostalCode(patch.postalCode());
        }
        if (patch.country() != null) {
            address.setCountry(patch.country().toUpperCase());
        }
        if (patch.isDefault() != null) {
            if (patch.isDefault()) {
                addressRepository.clearDefaultFor(userId);
            }
            address.setDefault(patch.isDefault());
        }
        return AddressResponse.from(address);
    }

    /**
     * Deletes an owned address. Removing the default promotes the next most recent address,
     * so the invariant "a user with addresses has a default" survives the delete.
     */
    @Transactional
    public void delete(Long userId, Long addressId) {
        Address address = owned(userId, addressId);
        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);

        if (wasDefault) {
            addressRepository.findAllByUserIdOrderByIsDefaultDescIdDesc(userId).stream()
                    .findFirst()
                    .ifPresent(next -> next.setDefault(true));
        }
    }

    /** Promotes one address to default and demotes the rest. */
    @Transactional
    public AddressResponse setDefault(Long userId, Long addressId) {
        Address address = owned(userId, addressId);
        addressRepository.clearDefaultFor(userId);
        address.setDefault(true);
        return AddressResponse.from(address);
    }

    private Address owned(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "address_not_found", "No address %d for this user".formatted(addressId)));
    }
}
