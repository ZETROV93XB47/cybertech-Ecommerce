<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=(realm.password && realm.registrationAllowed && !registrationDisabled??); section>

    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <#if realm.password>
            <form id="kc-form-login" class="cyb-form" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">

                <#if !usernameHidden??>
                    <div class="cyb-field">
                        <label for="username" class="cyb-label cyb-label-required">
                            <#if !realm.loginWithEmailAllowed>${msg("username")}
                            <#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}
                            <#else>${msg("email")}
                            </#if>
                        </label>
                        <input
                            tabindex="1"
                            id="username"
                            class="cyb-input"
                            name="username"
                            value="${(login.username!'')}"
                            type="text"
                            autofocus
                            autocomplete="username"
                            aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                        />
                        <#if messagesPerField.existsError('username','password')>
                            <span id="input-error" class="cyb-field-error" aria-live="polite">
                                ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                            </span>
                        </#if>
                    </div>
                </#if>

                <div class="cyb-field">
                    <label for="password" class="cyb-label cyb-label-required">${msg("password")}</label>
                    <div class="cyb-input-row">
                        <input
                            tabindex="2"
                            id="password"
                            class="cyb-input"
                            name="password"
                            type="password"
                            autocomplete="current-password"
                            aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                        />
                        <button class="cyb-input-toggle" type="button" aria-label="${msg("showPassword")}" aria-controls="password" data-password-toggle>
                            ${msg("showPassword")}
                        </button>
                    </div>
                    <#if usernameHidden?? && messagesPerField.existsError('username','password')>
                        <span id="input-error" class="cyb-field-error" aria-live="polite">
                            ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                        </span>
                    </#if>
                </div>

                <div class="cyb-row">
                    <#if realm.rememberMe && !usernameHidden??>
                        <label class="cyb-checkbox" for="rememberMe">
                            <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if>>
                            ${msg("rememberMe")}
                        </label>
                    <#else>
                        <span></span>
                    </#if>
                    <#if realm.resetPasswordAllowed>
                        <a tabindex="5" href="${url.loginResetCredentialsUrl}" class="cyb-link">${msg("doForgotPassword")}</a>
                    </#if>
                </div>

                <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>

                <button
                    tabindex="4"
                    class="cyb-button cyb-button--primary"
                    name="login"
                    id="kc-login"
                    type="submit"
                >
                    ${msg("doLogIn")}
                </button>
            </form>
        </#if>

        <#if realm.password && social.providers??>
            <div class="cyb-divider">${msg("identity-provider-login-label")}</div>
            <div class="cyb-social">
                <#list social.providers as p>
                    <a class="cyb-button cyb-button--ghost" id="social-${p.alias}" href="${p.loginUrl}">
                        <#if p.iconClasses?has_content>
                            <i class="${properties.kcCommonLogoIdP!} ${p.iconClasses!}" aria-hidden="true"></i>
                        </#if>
                        <span>${p.displayName!}</span>
                    </a>
                </#list>
            </div>
        </#if>

        <script>
            (function () {
                var btn = document.querySelector('[data-password-toggle]');
                var input = document.getElementById('password');
                if (!btn || !input) return;
                btn.addEventListener('click', function () {
                    var hidden = input.getAttribute('type') === 'password';
                    input.setAttribute('type', hidden ? 'text' : 'password');
                    btn.textContent = hidden ? '${msg("hidePassword")}' : '${msg("showPassword")}';
                });
            })();
        </script>

    <#elseif section = "info">
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <span>${msg("noAccount")}</span>
            <a tabindex="6" href="${url.registrationUrl}" class="cyb-link">${msg("doRegister")}</a>
        </#if>
    </#if>

</@layout.registrationLayout>
