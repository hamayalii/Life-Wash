package com.ghasl_service.demo.event;

import com.ghasl_service.demo.model.Order;
import org.springframework.context.ApplicationEvent;

public class OrderSubmittedEvent extends ApplicationEvent {

    private final Order order;

    public OrderSubmittedEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }
}
