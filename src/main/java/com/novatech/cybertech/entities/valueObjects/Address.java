package com.novatech.cybertech.entities.valueObjects;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@Builder
@ToString
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class Address implements Serializable {

    private String street;
    private String city;
    private String zipCode;
    private String country;

    // Vous pouvez ajouter des méthodes métier ici
    // ex: public boolean isInFrance() { return "FR".equals(country); }
}