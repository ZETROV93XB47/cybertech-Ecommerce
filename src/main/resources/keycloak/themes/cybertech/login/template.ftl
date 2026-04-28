<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false showAnotherWayIfPresent=true>
<!DOCTYPE html>
<html lang="${locale.currentLanguageTag!'en'}" class="cyb-html ${properties.kcHtmlClass!}">

<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
    <meta name="robots" content="noindex, nofollow">

    <title>${msg("loginTitle", (realm.displayName!realm.name)!"Cybertech")}</title>

    <link rel="icon" href="${url.resourcesPath}/img/favicon.ico" />

    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap" rel="stylesheet">

    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>

    <#if properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
        </#list>
    </#if>

    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
</head>

<body class="cyb-body ${bodyClass}">
<main class="cyb-shell">
    <section class="cyb-card" role="region" aria-labelledby="cyb-page-title">
        <header class="cyb-card__head">
            <a href="${properties.kcLogoLink!'/'}" class="cyb-brand" aria-label="Cybertech home">
                Cybertech
            </a>
            <h1 id="cyb-page-title" class="cyb-headline">
                <#nested "header">
            </h1>
            <#if displayRequiredFields>
                <p class="cyb-helper">
                    <span class="cyb-label-required"></span>
                    ${msg("requiredFields")}
                </p>
            </#if>
        </header>

        <#-- Global Keycloak alerts (success / error / info / warning) -->
        <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
            <div class="cyb-alert cyb-alert--${message.type}">
                <#if message.type = 'success'><span aria-hidden="true">&#10003;</span></#if>
                <#if message.type = 'warning'><span aria-hidden="true">&#9888;</span></#if>
                <#if message.type = 'error'><span aria-hidden="true">&#9888;</span></#if>
                <#if message.type = 'info'><span aria-hidden="true">&#9432;</span></#if>
                <span class="cyb-alert__text">${kcSanitize(message.summary)?no_esc}</span>
            </div>
        </#if>

        <div class="cyb-form-wrap">
            <#nested "form">
        </div>

        <#if displayInfo>
            <div class="cyb-foot">
                <#nested "info">
            </div>
        </#if>

        <#if auth?? && auth.showTryAnotherWayLink() && showAnotherWayIfPresent>
            <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post" class="cyb-form">
                <input type="hidden" name="tryAnotherWay" value="on"/>
                <button type="submit" class="cyb-button cyb-button--ghost" id="try-another-way">
                    ${msg("doTryAnotherWay")}
                </button>
            </form>
        </#if>

        <footer class="cyb-foot">
            <#if realm.internationalizationEnabled  && locale.supported?size gt 1>
                <small>
                    ${msg("languages")}:
                    <#list locale.supported as l>
                        <a href="${l.url}" <#if l.languageTag == locale.currentLanguageTag>aria-current="true"</#if>>${l.label}</a><#sep>&nbsp;&middot;&nbsp;</#sep>
                    </#list>
                </small>
            </#if>
        </footer>
    </section>
</main>
</body>
</html>
</#macro>
