# Dedicated HTTPS ingress HSTS configuration

This opt-in [overlay](values-overlay.yml) addresses Checkmarx A02 using the existing
HeaderHandler. It is for a **dedicated HTTPS ingress instance** with a browser-trusted
certificate. It disables HTTP, including the sidecar's normal plaintext egress port
9080. **Do not apply it to the usual co-located sidecar whose application calls
http://localhost:9080**: those calls would fail even while HTTPS probes stay healthy.
Use a separate ingress instance or configure HSTS at the trusted TLS edge instead.
The packaged defaults retain HTTP egress and do not enable HSTS.

Merge the overlay into the complete application values.yml using the normal external
configuration directory or config server. It is not a standalone configuration.
Preserve custom routes/chains and merge any existing `header.pathPrefixHeader` rules;
review overlapping prefixes so another rule cannot override the host policy.
The overlay does not copy or replace handler chains or the existing comma-string
`header.response.update` / `header.response.remove` settings.

The packaged default and admin chains now start with header. Direct favicon,
specification, Swagger UI, health and module routes include it explicitly. The root
prefix response rule applies headers when the response is sent, after the proxy
copies backend headers. It enforces one HSTS value, no-store and nosniff, and removes
X-Powered-By even when a backend supplies conflicting values. Custom routes and
unsupported-method fallback chains must also run header before terminating.

HeaderHandler does not distinguish HTTP from HTTPS listeners. This profile cannot
provide TLS-only HSTS in a single dual-listener process. Keep public HTTP redirect
handling outside this policy. With TLS offload, configure the trusted edge to emit
or preserve HSTS and prevent untrusted direct-origin access; client-supplied
forwarded-protocol headers do not establish trust.

SpecificationHstsConfigurationTest loads the packaged values and overlay from the
classpath, then initializes the real handler orchestration in isolated JVMs. It
loads header.yml through values substitution, retains production routes/chains,
and uses existing test keystores. Loopback checks cover favicon bytes, health,
spec/Swagger, admin routes and proxied success/redirect/error/not-found responses
with conflicting backend headers. A default-profile control checks plaintext
proxy access remains usable. External authentication/token integrations are disabled
only in the fixture; this is handler-pipeline qualification, not a deployment test.

The policy intentionally omits `includeSubDomains` and `preload`; either needs a
separate domain-wide compatibility review. HSTS is learned over a secure connection,
so first-visit protection requires an appropriate preload arrangement. See
[RFC 6797](https://www.rfc-editor.org/rfc/rfc6797.html).

Before closing the partner finding, deploy the reviewed configuration and capture
public HTTPS headers for the favicon, normal, redirect and error responses. Verify
one intended HSTS value and direct-origin access restrictions, then rerun the scan.
Loopback tests do not establish the partner deployment's state.
