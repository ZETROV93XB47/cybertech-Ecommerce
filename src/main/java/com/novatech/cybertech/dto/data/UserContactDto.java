package com.novatech.cybertech.dto.data;

import com.novatech.cybertech.entities.enums.CommunicationChanel;
import lombok.*;

@Setter
@Getter
@Builder
@ToString
@EqualsAndHashCode
public class UserContactDto {
    private String name;
    private String email;
    private String phoneNumber;
    private CommunicationChanel defaultCommunicationChanel;
}
