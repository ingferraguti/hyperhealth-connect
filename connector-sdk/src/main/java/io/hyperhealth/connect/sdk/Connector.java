package io.hyperhealth.connect.sdk;

import io.hyperhealth.connect.envelope.IntegrationEnvelope;
import java.util.concurrent.CompletionStage;

/** Framework-neutral connector lifecycle and delivery boundary. */
public interface Connector extends AutoCloseable {

    ConnectorDescriptor descriptor();

    void start(ConnectorContext context);

    CompletionStage<DeliveryReceipt> deliver(IntegrationEnvelope envelope);

    ConnectorHealth health();

    @Override
    void close();
}

