<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true displayMessage=!messagesPerField.existsError('username'); section>

    <#if section = "header">
        ${msg("emailForgotTitle")}
    <#elseif section = "form">
        <form id="kc-reset-password-form" class="cyb-form" action="${url.loginAction}" method="post">
            <p class="cyb-subhead">${msg("emailInstruction")}</p>

            <div class="cyb-field">
                <label for="username" class="cyb-label cyb-label-required">
                    <#if !realm.loginWithEmailAllowed>${msg("username")}
                    <#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}
                    <#else>${msg("email")}
                    </#if>
                </label>
                <input
                    type="text"
                    id="username"
                    class="cyb-input"
                    name="username"
                    value="${(auth.attemptedUsername!'')}"
                    autocomplete="username"
                    autofocus
                    aria-invalid="<#if messagesPerField.existsError('username')>true</#if>"
                />
                <#if messagesPerField.existsError('username')>
                    <span class="cyb-field-error" aria-live="polite">${kcSanitize(messagesPerField.get('username'))?no_esc}</span>
                </#if>
            </div>

            <div class="cyb-row">
                <a href="${url.loginUrl}" class="cyb-link">${kcSanitize(msg("backToLogin"))?no_esc}</a>
                <span></span>
            </div>

            <button class="cyb-button cyb-button--primary" type="submit">${msg("doSubmit")}</button>
        </form>
    <#elseif section = "info">
        <p class="cyb-helper">${msg("emailInstruction")}</p>
    </#if>
</@layout.registrationLayout>
