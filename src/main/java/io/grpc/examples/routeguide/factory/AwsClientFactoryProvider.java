package io.grpc.examples.routeguide.factory;

import java.util.logging.Logger;

/**
 * Provider that selects the appropriate AWS client factory based on environment.
 * Uses the APP_ENV environment variable to determine which factory to use:
 * - "local" or "development" -> LocalDevelopmentAwsClientFactory
 * - "production" or unset -> ProductionAwsClientFactory
 */
public class AwsClientFactoryProvider {

    private static final Logger logger = Logger.getLogger(AwsClientFactoryProvider.class.getName());
    private static AwsClientFactory instance;

    /**
     * Get the appropriate AWS client factory based on environment.
     */
    public static synchronized AwsClientFactory getFactory() {
        if (instance == null) {
            String appEnv = System.getenv("APP_ENV");
            if (appEnv == null) {
                appEnv = "production";
            }
            appEnv = appEnv.toLowerCase();

            logger.info("Initializing AWS client factory for environment: " + appEnv);

            switch (appEnv) {
                case "local":
                case "development":
                    instance = new LocalDevelopmentAwsClientFactory();
                    break;
                case "production":
                default:
                    instance = new ProductionAwsClientFactory();
                    break;
            }

            logger.info("Using factory: " + instance.getClass().getSimpleName());
        }
        return instance;
    }

    /**
     * Set a custom factory (useful for testing).
     */
    public static synchronized void setFactory(AwsClientFactory factory) {
        instance = factory;
    }

    /**
     * Reset the factory instance (useful for testing).
     */
    public static synchronized void reset() {
        instance = null;
    }
}
