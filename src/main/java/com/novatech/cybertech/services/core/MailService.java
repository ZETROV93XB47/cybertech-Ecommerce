package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.EmailDto;

public interface MailService {
    void sendEmail(final EmailDto emailDto);
}
