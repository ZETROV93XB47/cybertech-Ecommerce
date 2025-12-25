package com.novatech.cybertech.annotation;

import com.novatech.cybertech.entities.enums.CommunicationChanel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommunicationTypeHandler {
    CommunicationChanel value();
}
