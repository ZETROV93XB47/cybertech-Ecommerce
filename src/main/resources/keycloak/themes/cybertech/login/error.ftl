<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${msg("errorTitle")}
    <#elseif section = "form">
        <div class="cyb-alert cyb-alert--error" role="alert">
            <span aria-hidden="true">&#9888;</span>
            <span class="cyb-alert__text">${kcSanitize(message.summary)?no_esc}</span>
        </div>

        <#if skipLink??>
        <#else>
            <#if client?? && client.baseUrl?has_content>
                <a href="${client.baseUrl}" class="cyb-button cyb-button--ghost" id="backToApplication">
                    ${kcSanitize(msg("backToApplication"))?no_esc}
                </a>
            </#if>
        </#if>
    </#if>
</@layout.registrationLayout>
