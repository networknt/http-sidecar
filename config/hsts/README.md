# HTTPS-only HSTS configuration

This opt-in overlay addresses Checkmarx A02 (`light-rest-4j` favicon HSTS) in a
consuming application using the existing `HeaderHandler`.

Merge [values-overlay.yml](values-overlay.yml) into the active application's
complete `values.yml`. It is not a standalone routing configuration. Preserve
any application-specific handlers, routes and response-header updates: combine
the header maps, and move `header` to the beginning of every applicable chain
rather than discarding custom chain entries. The supplied chains match the
packaged sidecar defaults. The overlay enables HTTPS, disables plaintext HTTP,
and sets `Strict-Transport-Security: max-age=31536000` without `includeSubDomains`
or `preload`.

Use a certificate trusted by the consuming browsers; the bundled development
certificate is not a production certificate. Select the merged configuration
through the application's normal external configuration directory or config
server. No running instance is changed by the presence of this overlay.

The packaged sidecar's direct favicon, Swagger UI, specification, health and
module-management routes now include `header` explicitly. This closes the route
bypass that otherwise prevents configured response headers from reaching them.
The overlay also puts the response-header policy before short-circuiting
handlers in the default and admin chains. Header policy therefore reaches the
supported routes' normal, rejection and error responses. Review any custom
routes, unsupported-method fallback or proxy-generated response separately.

The default sidecar can still use plaintext HTTP internally and does not emit
HSTS by default. Do not apply this overlay to a publicly accessible plaintext
listener: generic `HeaderHandler` does not check the request scheme. For public
HTTP-to-HTTPS redirects, keep the plaintext redirect handler outside this HSTS
chain. With TLS termination at an edge, ensure the edge preserves or emits one
HSTS header on public HTTPS responses and that the origin cannot be reached
through an untrusted direct path. Client-supplied forwarded-protocol headers
are not a trust decision.

`SpecificationHstsConfigurationTest` reads both the packaged values and this
overlay, checks the actual route/chain ordering, and tests the real favicon
handler and header middleware over TLS. It covers normal, redirect, not-found
and exception responses and checks the unconfigured control and preserved icon.
The TLS certificate is generated only for the test and is not checked in.

Before closing the partner finding, deploy the reviewed merged configuration,
capture public HTTPS headers for the favicon and other supported response
paths, confirm one intended HSTS value, and rerun the partner scan. The
configuration and loopback tests do not establish the partner deployment's
state. See [RFC 6797](https://www.rfc-editor.org/rfc/rfc6797.html).
