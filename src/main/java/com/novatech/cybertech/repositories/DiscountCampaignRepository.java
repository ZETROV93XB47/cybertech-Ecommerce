package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.DiscountCampaignEntity;
import com.novatech.cybertech.entities.enums.DiscountType;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiscountCampaignRepository extends CrudBaseRepository<DiscountCampaignEntity, Long> {

    Optional<DiscountCampaignEntity> findByDiscountType(DiscountType discountType);

    boolean existsByDiscountType(DiscountType discountType);

    void deleteByDiscountType(DiscountType discountType);
}
