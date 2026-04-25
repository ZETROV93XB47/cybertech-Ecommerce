package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.DiscountCampaignEntity;
import com.novatech.cybertech.entities.enums.DiscountType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscountCampaignRepository extends CrudBaseRepository<DiscountCampaignEntity, Long> {

    Optional<DiscountCampaignEntity> findByDiscountType(DiscountType discountType);

    boolean existsByDiscountType(DiscountType discountType);

    @Modifying
    @Transactional
    void deleteByDiscountType(DiscountType discountType);

    List<DiscountCampaignEntity> findByEnabledTrue();
}
