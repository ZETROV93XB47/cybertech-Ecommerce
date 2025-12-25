package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.BankCardEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface BankCardRepository extends CrudBaseRepository<BankCardEntity, Long> {
}