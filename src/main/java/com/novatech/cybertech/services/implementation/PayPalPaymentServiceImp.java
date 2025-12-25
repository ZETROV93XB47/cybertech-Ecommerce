package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.core.PayPalPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.novatech.cybertech.entities.enums.PaymentStatus.SUCCESS;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayPalPaymentServiceImp implements PayPalPaymentService {

    private final OrderRepository orderRepository;

    @Override
    public void onSuccess(final UUID orderUUID) {
        final OrderEntity order = orderRepository.findByUuid(orderUUID).orElseThrow(() -> new OrderNotFoundException("no order with the provided informations (UUID) found"));

        //Faire quelques vérifications métiers avant de ship la commande
        // Par exemple vérifier le statut de la commande (si elle est vraiment toujours en PENDING_PAMENT)
        //

        order.setStatus(OrderStatus.SHIPPED);
        order.getPaymentEntity().setPaymentStatus(SUCCESS);
    }

    @Override
    public void onFailure() {

    }
}
