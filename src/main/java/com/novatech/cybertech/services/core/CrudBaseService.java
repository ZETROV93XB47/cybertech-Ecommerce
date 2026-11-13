package com.novatech.cybertech.services.core;

/**
 * Generic CRUD contract. {@code J} carries the caller's identity (Keycloak subject, i.e.
 * {@code Jwt#getSubject()}) alongside every operation, so implementations can enforce
 * ownership or audit the actor without a separate ad hoc overload per service.
 * {@code null} is a valid {@code J} for endpoints that are genuinely anonymous (e.g. public
 * user registration, public product lookup) — implementations must tolerate it.
 */
public interface CrudBaseService<T, U, W, V, J> {

    V getByUUID(T t, J jwt);

    V create(U u, J jwt);

    V update(W w, J jwt);

    void deleteByUUID(T t, J jwt);

}