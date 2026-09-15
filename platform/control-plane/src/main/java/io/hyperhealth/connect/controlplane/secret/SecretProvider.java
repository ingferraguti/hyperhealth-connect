package io.hyperhealth.connect.controlplane.secret;

/** Qualified classes of external secret stores; the Platform DB never stores provider paths. */
public enum SecretProvider {
    HASHICORP_VAULT,
    AWS_SECRETS_MANAGER,
    AZURE_KEY_VAULT,
    GCP_SECRET_MANAGER,
    KUBERNETES_SECRETS_STORE_CSI,
    EXTERNAL_BROKER
}
