package io.hyperhealth.connect.controlplane.secret;

/** Closed-set purpose binding used to prevent a reference from being reused as another credential. */
public enum SecretPurpose {
    ENDPOINT_BASIC_AUTH,
    ENDPOINT_API_TOKEN,
    OAUTH_CLIENT_CREDENTIAL,
    TLS_CLIENT_PRIVATE_KEY,
    TLS_CLIENT_CERTIFICATE,
    DATABASE_CREDENTIAL
}
