# Hybrid Android content and bridge trust

Mobile SDK hybrid apps expose native Salesforce functionality to JavaScript.
Review the content loaded into that WebView as application code, including scripts
loaded by remote pages. A Cordova upgrade does not make untrusted page content
safe to run with the app's plugins.

## Current configuration

The Cordova Android 15.1.0 upgrade preserves these existing Mobile SDK choices:

| Area | Behavior | Review implication |
| --- | --- | --- |
| Local start pages | `SalesforceDroidGapActivity.loadLocalStartPage()` loads `file:///android_asset/www/…`. | Bundled HTML and JavaScript are trusted app content. |
| File access | Hybrid configuration enables `AndroidInsecureFileModeEnabled`; `SalesforceWebViewClient` also enables WebView file access. | Removing one preference alone does not migrate the complete loading path. |
| Localhost resources | `SalesforceWebViewClient` intercepts localhost resource requests and reads bundled content through Cordova's resource API. | Test resource loading and path boundaries separately from top-level navigation. |
| Remote start pages | Mobile SDK resolves the configured page using the authenticated instance and loads it in the hybrid WebView. | The remote page, its administrators and its script dependencies form part of the app's trust boundary. |
| Redirect destinations | The production redirect resolver checks URL structure and the configured Cordova navigation policy, with an exact configured start-page fallback. | Keep navigation rules narrow; this check does not sanitize page HTML or scripts. |
| Salesforce plugins | Installed bridges can perform authentication, network, account and local data operations. | Treat script injection into trusted content as potentially reaching native capabilities. |

These are inherited compatibility choices, not new permissions introduced by the
platform version change. No exploit or complete security assurance follows from
this configuration review alone.

## Application review

Use a Content Security Policy appropriate to the app's actual resources. Prefer
bundled scripts or tightly controlled HTTPS sources and avoid executing HTML or
JavaScript assembled from untrusted input. Review the CSP served by a remote page,
not just a local bootstrap page. An allowed Salesforce hostname is not evidence
that every page or third-party script on that host is safe.

Review navigation, network access and external intents individually. Cordova's
allow lists cover different operations and do not replace CSP, server-side
authorization or input validation. Browser-origin restrictions also do not
describe all requests made by native plugins. Avoid broadening access rules to
make an upgrade pass.

Never place refresh tokens or other credentials in static resources, diagnostic
reports or source control. Keep signing and test credentials in protected local
configuration. Follow the repository's [security reporting policy](../../SECURITY.md)
for a suspected vulnerability.

## Verification and migration boundaries

The existing focused `SalesforceRedirectDestinationPolicyTest` exercises allowed
Salesforce destinations, lookalike hosts, path traversal, malformed URLs,
unsupported schemes and rejected refresh destinations. Those checks concern the
redirect resolver; they do not establish full WebView isolation or protection
against script injection.

An app review should additionally exercise local startup and bridge access,
remote startup after authentication, allowed and rejected navigation, cached and
offline content, CSP violations, external intents and subresource loading. Use
an owned test device and controlled pages. Inspect the installed plugin list and
the JavaScript actually shipped in both the APK and remote server resources.

A move from file-based content to a secure local origin needs a separate
compatibility assessment of authentication, cookies, storage, relative URLs,
localhost interception, offline content and plugins. Changing file access or the
origin silently during a Cordova version update can break existing applications.

See Apache Cordova's [security guide](https://cordova.apache.org/docs/en/latest/guide/appdev/security/index.html),
[allow-list guide](https://cordova.apache.org/docs/en/latest/guide/appdev/allowlist/index.html)
and [Android configuration reference](https://cordova.apache.org/docs/en/latest/config_ref/).
