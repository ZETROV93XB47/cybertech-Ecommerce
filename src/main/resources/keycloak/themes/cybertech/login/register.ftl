<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('firstName','lastName','email','username','password','password-confirm') displayRequiredFields=true; section>

    <#if section = "header">
        ${msg("registerTitle")}
    <#elseif section = "form">
        <form id="kc-register-form" class="cyb-form" action="${url.registrationAction}" method="post">

            <div class="cyb-field">
                <label for="firstName" class="cyb-label cyb-label-required">${msg("firstName")}</label>
                <input
                    type="text"
                    id="firstName"
                    class="cyb-input"
                    name="firstName"
                    value="${(register.formData.firstName!'')}"
                    autocomplete="given-name"
                    aria-invalid="<#if messagesPerField.existsError('firstName')>true</#if>"
                />
                <#if messagesPerField.existsError('firstName')>
                    <span class="cyb-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('firstName'))?no_esc}</span>
                </#if>
            </div>

            <div class="cyb-field">
                <label for="lastName" class="cyb-label cyb-label-required">${msg("lastName")}</label>
                <input
                    type="text"
                    id="lastName"
                    class="cyb-input"
                    name="lastName"
                    value="${(register.formData.lastName!'')}"
                    autocomplete="family-name"
                    aria-invalid="<#if messagesPerField.existsError('lastName')>true</#if>"
                />
                <#if messagesPerField.existsError('lastName')>
                    <span class="cyb-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('lastName'))?no_esc}</span>
                </#if>
            </div>

            <div class="cyb-field">
                <label for="email" class="cyb-label cyb-label-required">${msg("email")}</label>
                <input
                    type="email"
                    id="email"
                    class="cyb-input"
                    name="email"
                    value="${(register.formData.email!'')}"
                    autocomplete="email"
                    aria-invalid="<#if messagesPerField.existsError('email')>true</#if>"
                />
                <#if messagesPerField.existsError('email')>
                    <span class="cyb-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('email'))?no_esc}</span>
                </#if>
            </div>

            <#if !realm.registrationEmailAsUsername>
                <div class="cyb-field">
                    <label for="username" class="cyb-label cyb-label-required">${msg("username")}</label>
                    <input
                        type="text"
                        id="username"
                        class="cyb-input"
                        name="username"
                        value="${(register.formData.username!'')}"
                        autocomplete="username"
                        aria-invalid="<#if messagesPerField.existsError('username')>true</#if>"
                    />
                    <#if messagesPerField.existsError('username')>
                        <span class="cyb-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('username'))?no_esc}</span>
                    </#if>
                </div>
            </#if>

            <#if passwordRequired??>
                <div class="cyb-field">
                    <label for="password" class="cyb-label cyb-label-required">${msg("password")}</label>
                    <input
                        type="password"
                        id="password"
                        class="cyb-input"
                        name="password"
                        autocomplete="new-password"
                        aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true</#if>"
                    />
                    <#if messagesPerField.existsError('password')>
                        <span class="cyb-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('password'))?no_esc}</span>
                    </#if>
                </div>

                <div class="cyb-field">
                    <label for="password-confirm" class="cyb-label cyb-label-required">${msg("passwordConfirm")}</label>
                    <input
                        type="password"
                        id="password-confirm"
                        class="cyb-input"
                        name="password-confirm"
                        autocomplete="new-password"
                        aria-invalid="<#if messagesPerField.existsError('password-confirm')>true</#if>"
                    />
                    <#if messagesPerField.existsError('password-confirm')>
                        <span class="cyb-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</span>
                    </#if>
                </div>
            </#if>

            <#if recaptchaRequired??>
                <div class="cyb-field">
                    <div class="g-recaptcha" data-size="compact" data-sitekey="${recaptchaSiteKey}"></div>
                </div>
            </#if>

            <div class="cyb-row">
                <a href="${url.loginUrl}" class="cyb-link">${kcSanitize(msg("backToLogin"))?no_esc}</a>
                <span></span>
            </div>

            <button class="cyb-button cyb-button--primary" type="submit">${msg("doRegister")}</button>
        </form>
    </#if>
</@layout.registrationLayout>
