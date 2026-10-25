Negative binary-compatibility fixture: `com/networknt/chaos/ChaosMonkeyPostHandler.class`, extracted unchanged from Maven Central `com.networknt:chaos-monkey:2.4.1`.

Jar SHA-256: `e1d2b054a7d8f6390ca0999d2decd858c7cef96fcf5f2c6522a7585f57feefdc`.

Reproduce with `unzip -p chaos-monkey-2.4.1.jar com/networknt/chaos/ChaosMonkeyPostHandler.class`. The `.bin` suffix prevents class loading. The test checks this known Jackson 2 binary is rejected, then audits the actual handler supplied on the application classpath. Recompiling source in CI does not remove the negative control.
