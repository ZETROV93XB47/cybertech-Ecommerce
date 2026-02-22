package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.entities.StockEntity;
import com.novatech.cybertech.entities.enums.ReservationStatus;
import com.novatech.cybertech.repositories.StockRepository;
import com.novatech.cybertech.services.core.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanUpExpiredStockReservationsTasklet extends BaseTasklet {

    private final StockRepository stockRepository;
    private final StockService stockService;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution stepContribution, StepArguments stepArguments) {
        log.info("Starting CleanUpExpiredStockReservationsTasklet");

        // On prend une marge de sécurité : 15 minutes (le TTL Redis est de 10 min)
        // Si une réservation est ACTIVE et vieille de plus de 15 min, c'est qu'elle a échappé au listener Redis.
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);

        // Note : Idéalement, il faudrait une méthode findByReservationStatusAndCreatedAtBefore dans le repository
        // pour éviter de charger toute la table. Ici on filtre en Java pour l'exemple.
        List<StockEntity> allStocks = stockRepository.findAll();

        Set<UUID> expiredOrderUuids = allStocks.stream()
                .filter(s -> s.getReservationStatus() == ReservationStatus.ACTIVE)
                .filter(s -> s.getCreatedAt().isBefore(threshold))
                .map(StockEntity::getOrderUuid)
                .collect(Collectors.toSet());

        if (expiredOrderUuids.isEmpty()) {
            log.info("No expired stock reservations found to clean up.");
            stepContribution.setExitStatus(ExitStatus.COMPLETED);
            return RepeatStatus.FINISHED;
        }

        log.info("Found {} orders with stuck reservations. Releasing stock...", expiredOrderUuids.size());

        for (UUID orderUuid : expiredOrderUuids) {
            // releaseStock gère la suppression en base et le nettoyage Redis
            stockService.releaseStock(orderUuid);
        }

        stepContribution.setExitStatus(ExitStatus.COMPLETED);
        log.info("CleanUpExpiredStockReservationsTasklet finished");
        return RepeatStatus.FINISHED;
    }
}