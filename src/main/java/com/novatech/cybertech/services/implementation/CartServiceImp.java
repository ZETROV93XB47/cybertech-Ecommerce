package com.novatech.cybertech.services.implementation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.*;
import com.novatech.cybertech.mappers.entity.CartMapper;
import com.novatech.cybertech.repositories.CartRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImp implements CartService {

    private final CartMapper cartMapper;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;


    @Override
    @Transactional
    @CachePut(cacheNames = "cart", key = "#keycloakId")
    public CartResponseDto addItemsToCart(final CartCreateRequestDto cartCreateRequestDto, final String keycloakId) {

        log.info("cart request dto : {}", cartCreateRequestDto);

        final Map<UUID, Integer> productsToAdd = cartCreateRequestDto.getCartItemAddRequestDtos().stream().collect(Collectors.toMap(CartItemAddRequestDto::getProductUuid, CartItemAddRequestDto::getQuantity));
        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

        // Récupération des produits en une seule requête pour optimiser les performances
        List<ProductEntity> products = productRepository.findAllByUuidIn(productsToAdd.keySet());
        Map<UUID, ProductEntity> productMap = products.stream().collect(Collectors.toMap(ProductEntity::getUuid, p -> p));

        log.info("products to add : {}", productsToAdd);
        log.info("productsMap : {}", productMap);
        log.info("products : {}", products);

        CartEntity cartEntity = user.getCartEntity();

        log.info("cart : {}", cartEntity);

        // 1. Créer le panier s'il n'existe pas
        if (cartEntity == null) {
            cartEntity = CartEntity.builder()
                    .userEntity(user)
                    .cartItems(new ArrayList<>())
                    .uuid(UuidCreator.getTimeOrderedEpoch())
                    .build();
            // Important : lier le panier à l'utilisateur si ce n'est pas fait automatiquement par le save du cart
            // user.setCartEntity(cartEntity);
        }

        // Pour chaque produit à ajouter
        for (Map.Entry<UUID, Integer> entry : productsToAdd.entrySet()) {
            UUID productUuid = entry.getKey();
            Integer quantity = entry.getValue();
            ProductEntity product = productMap.get(productUuid);
            if (product == null) {
                throw new ProductNotFoundException("No product with the UUID : " + productUuid + " found");
            }

            // 2. Vérifier si le produit est déjà dans le panier
            CartEntity finalCartEntity = cartEntity;
            Optional<CartItemEntity> existingItem = finalCartEntity.getCartItems().stream()
                    .filter(item -> item.getProductEntity().getUuid().equals(productUuid))
                    .findFirst();

            log.info("existingItem : {} and old quantity : {}", existingItem, quantity);

            int newQuantity = existingItem.map(item -> {
                int res = item.getQuantity() + quantity;

                log.info("newQuantity in lambda : {}", res);

                return res;

            }).orElse(quantity);

            log.info("newQuantity : {}", newQuantity);

            // 3. Vérifier le stock (Stock total vs Stock réservé + Quantité demandée totale)
            if (product.getReservedStock() + newQuantity > product.getStock()) {
                throw new NotEnoughStockException("Not enough stock for product " + product.getName() + ". Available: " + (product.getStock() - product.getReservedStock()));
            }

            log.info("product stock : {}", product.getStock());
            log.info("product reserved stock : {}", product.getReservedStock());

            if (existingItem.isPresent()) {
                // Mise à jour de la quantité existante
                log.info("existing item before quantity update: {}", existingItem);
                existingItem.get().increaseQuantity(quantity);
                log.info("existing item after quantity update: {}", existingItem);
            } else {
                // Ajout d'un nouvel item
                final CartItemEntity newItem = CartItemEntity.builder()
                        .quantity(quantity)
                        .unitPrice(product.getPrice())
                        .productEntity(product)
                        .cart(cartEntity) // Important : Lier l'enfant au parent
                        .uuid(UuidCreator.getTimeOrderedEpoch())
                        .build();

                cartEntity.getCartItems().add(newItem);
            }
        }

        return cartMapper.mapFromEntityToResponseDto(cartRepository.save(cartEntity));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "cart", key = "#keycloakId", unless = "#result == null || #result.cartUuid == null")
    public CartResponseDto getCart(final String keycloakId) {
        
        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

        return user.getCartEntity() == null ? new CartResponseDto() : cartMapper.mapFromEntityToResponseDto(user.getCartEntity());
    }

    @Override
    @Transactional
    @CachePut(cacheNames = "cart", key = "#keycloakId") // Supprime le cache pour forcer le rechargement
    public CartResponseDto removeItemFromCart(final UUID productUuid, final String keycloakId) {
        
        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));
        final CartEntity cart = user.getCartEntity();

        if (cart != null && cart.getCartItems() != null) {
            boolean removed = cart.getCartItems().removeIf(item -> item.getProductEntity().getUuid().equals(productUuid));

            if (removed) {
                return cartMapper.mapFromEntityToResponseDto(cartRepository.save(cart));
            }
        }
        throw new CannotRemoveItemFromEmptyCartException("Cannot remove item already absent from cart.");
    }

    @Override
    @Transactional
    @CachePut(cacheNames = "cart", key = "#keycloakId")
    public CartResponseDto decreaseQuantity(final CartItemRemoveRequestDto cartItemRemoveRequestDto, final String keycloakId) {
        
        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));
        final CartEntity cart = user.getCartEntity();

        if (cart == null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new CartIsEmptyException("Cannot decrease quantity from an empty cart.");
        }

        final CartItemEntity cartItemToDecrease = cart.getCartItems().stream()
                .filter(item -> item.getProductEntity().getUuid().equals(cartItemRemoveRequestDto.getProductUuid()))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException("Product doesn't exist in cart"));

        final Integer updateResult = cartItemToDecrease.decreaseQuantity(cartItemRemoveRequestDto.getQuantity());

        // Le service gère la logique de la collection (suppression de l'item)

        if (updateResult <= 0) {
            cart.getCartItems().remove(cartItemToDecrease);
        }
        return cartMapper.mapFromEntityToResponseDto(cartRepository.save(cart));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "cart", key = "#keycloakId") // Supprime le cache
    public void clearCart(final String keycloakId) {
        
        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));
        final CartEntity cart = user.getCartEntity();

        if (cart != null && cart.getCartItems() != null) {
            cart.getCartItems().clear(); // Vide la liste
            cartRepository.save(cart);   // Sauvegarde l'état vide
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<CartResponseDto> getAll() {
        return cartMapper.mapFromEntityToResponseDto(cartRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponseDto getByUUID(UUID uuid) {
        return cartMapper.mapFromEntityToResponseDto(cartRepository.findByUuid(uuid).orElseThrow(() -> new CartNotFoundException("No cart with the UUID : " + uuid + " found")));
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<CartResponseDto> getByUUIDs(Collection<UUID> uuids) {
        return cartMapper.mapFromEntityToResponseDto(cartRepository.findAllByUuidIn(uuids));
    }

    @Override
    @Transactional
    public CartResponseDto create(CartCreateRequestDto cartCreateRequestDto) {
        return cartMapper.mapFromEntityToResponseDto(cartRepository.save(cartMapper.mapFromCreationRequestToEntity(cartCreateRequestDto)));
    }

    @Override
    @Transactional
    public CartResponseDto update(final CartItemRemoveRequestDto cartCreateRequestDto) {
        return cartMapper.mapFromEntityToResponseDto(cartRepository.save(cartMapper.mapFromUpdateRequestToEntity(cartCreateRequestDto)));
    }

    @Override
    @Transactional
    public void deleteByUUID(UUID uuid) {
        cartRepository.deleteByUuid(uuid);
    }

    @Override
    @Transactional
    public void deleteByUUIDs(Collection<UUID> uuids) {
        cartRepository.deleteAllByUuidIn(uuids);
    }
}
