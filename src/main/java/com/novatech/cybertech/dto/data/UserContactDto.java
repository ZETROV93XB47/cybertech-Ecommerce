package com.novatech.cybertech.dto.data;

import com.novatech.cybertech.entities.enums.CommunicationChanel;
import lombok.*;

/**
 * <p><b>Phase 3 enabler:</b> {@code @NoArgsConstructor} + {@code @AllArgsConstructor}
 * are required so Jackson 3 can deserialize {@link NotificationRedrivePayload}
 * (which carries this DTO as a nested field) when the batch redrive tasklet
 * rebuilds a {@link NotificationContext} from the persisted JSON snapshot.
 * Without them Jackson reports "no Creators, like default constructor, exist".
 */
@Setter
@Getter
@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class UserContactDto {
    private String name;
    private String email;
    private String phoneNumber;
    private CommunicationChanel defaultCommunicationChanel;
}
