/*
 * Copyright 2019 dc-square GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hivemq.embedded.internal;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Injector;
import com.hivemq.HiveMQServer;
import com.hivemq.bootstrap.LoggingBootstrap;
import com.hivemq.bootstrap.ioc.GuiceBootstrap;
import com.hivemq.common.shutdown.HiveMQShutdownHook;
import com.hivemq.common.shutdown.ShutdownHooks;
import com.hivemq.configuration.ConfigurationBootstrap;
import com.hivemq.configuration.HivemqId;
import com.hivemq.configuration.info.SystemInformationImpl;
import com.hivemq.configuration.service.FullConfigurationService;
import com.hivemq.embedded.EmbeddedHiveMQ;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.annotations.Nullable;
import com.hivemq.persistence.PersistenceStartup;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * @author Georg Held
 */
public class EmbeddedHiveMQImpl implements EmbeddedHiveMQ {

    private final @Nullable Path conf;
    private final @NotNull Path extensions;
    private final @NotNull Path data;
    private final @NotNull SystemInformationImpl systemInformation;
    private final @NotNull MetricRegistry metricRegistry;
    private final @NotNull FullConfigurationService configurationService;
    private @Nullable Injector injector;

    private @Nullable CompletableFuture<Void> startFuture;
    private @Nullable CompletableFuture<Void> stopFuture;

    EmbeddedHiveMQImpl(final @Nullable Path conf, final @NotNull Path extensions, final @NotNull Path data) {

        this.conf = conf;
        this.extensions = extensions;
        this.data = data;


        systemInformation = new SystemInformationImpl();
        metricRegistry = new MetricRegistry();

        //Setup Logging
        LoggingBootstrap.prepareLogging();
        LoggingBootstrap.initLogging(systemInformation.getConfigFolder());

        systemInformation.setEmbedded(true);
        configurationService = ConfigurationBootstrap.bootstrapConfig(systemInformation);
//        reduceResources();
    }

    public void bootstrapInjector() {
        if (injector == null) {
            final HivemqId hiveMQId = new HivemqId();
            final Injector persistenceInjector =
                    GuiceBootstrap.persistenceInjector(
                            systemInformation, metricRegistry, hiveMQId, configurationService);

            try {
                persistenceInjector.getInstance(PersistenceStartup.class).finish();
            } catch (final InterruptedException e) {
                System.out.println("ERROR: Persistence Startup interrupted.");
            }

            injector =
                    GuiceBootstrap.bootstrapInjector(systemInformation, metricRegistry, hiveMQId, configurationService,
                            persistenceInjector);
        }
    }

    @Override
    public synchronized @NotNull CompletableFuture<Void> start() {
        if (startFuture == null) {
            startFuture = new CompletableFuture<>();
            CompletableFuture.runAsync(() -> {
                bootstrapInjector();
                final HiveMQServer instance = injector.getInstance(HiveMQServer.class);
                try {
                    instance.start();
                } catch (final Exception e) {
                    throw new HiveMQServerException(e);
                }
            }).whenComplete(HiveMQServerException.handler(startFuture));
        }
        return startFuture;
    }

    @Override
    public synchronized @NotNull CompletableFuture<Void> stop() {
        if (startFuture == null) {
            stopFuture = CompletableFuture.completedFuture(null);
        } else if (stopFuture == null) {
            startFuture = CompletableFuture.runAsync(() -> {
                final ShutdownHooks instance = injector.getInstance(ShutdownHooks.class);
                for (final HiveMQShutdownHook hiveMQShutdownHook : instance.getAsyncShutdownHooks()) {
                    hiveMQShutdownHook.run();
                }

//            if (tempFolder != null) {
//                recursiveDelete(tempFolder);
//            }
            });
        }
        return stopFuture;
    }

    @Override
    public @Nullable MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

//    private static final int persistenceBucketCountDefault = InternalConfigurations.PERSISTENCE_BUCKET_COUNT.get();
//    private static final int payloadPersistenceBucketCountDefault =
//            InternalConfigurations.PAYLOAD_PERSISTENCE_BUCKET_COUNT.get();
//    private static final int payloadPersistenceCleanupThreadsDefault =
//            InternalConfigurations.PAYLOAD_PERSISTENCE_CLEANUP_THREADS.get();
//    private static final int mqttEventExecutorThreadCountDefault =
//            InternalConfigurations.MQTT_EVENT_EXECUTOR_THREAD_COUNT.get();
//    private static final int singleWriterThreadPoolSizeDefault =
//            InternalConfigurations.SINGLE_WRITER_THREAD_POOL_SIZE.get();
//    private static final int persistenceStartupThreadPoolSizeDefault =
//            InternalConfigurations.PERSISTENCE_STARTUP_THREAD_POOL_SIZE.get();
//
//    private void reduceResources() {
//
//        //only use 4 buckets per persistence to reduce count of open files
//        InternalConfigurations.PERSISTENCE_BUCKET_COUNT.set(4);
//        InternalConfigurations.PAYLOAD_PERSISTENCE_BUCKET_COUNT.set(4);
//
//        //reduce thread count to have less context switches
//        InternalConfigurations.PAYLOAD_PERSISTENCE_CLEANUP_THREADS.set(2);
//        InternalConfigurations.MQTT_EVENT_EXECUTOR_THREAD_COUNT.set(2);
//        InternalConfigurations.SINGLE_WRITER_THREAD_POOL_SIZE.set(2);
//        InternalConfigurations.PERSISTENCE_STARTUP_THREAD_POOL_SIZE.set(2);
//    }
//
//    public void cleanup() {
//

//
//        //only use 4 buckets per persistence to reduce count of open files
//        InternalConfigurations.PERSISTENCE_BUCKET_COUNT.set(persistenceBucketCountDefault);
//        InternalConfigurations.PAYLOAD_PERSISTENCE_BUCKET_COUNT.set(payloadPersistenceBucketCountDefault);
//
//        //reduce thread count to have less context switches
//        InternalConfigurations.PAYLOAD_PERSISTENCE_CLEANUP_THREADS.set(payloadPersistenceCleanupThreadsDefault);
//        InternalConfigurations.MQTT_EVENT_EXECUTOR_THREAD_COUNT.set(mqttEventExecutorThreadCountDefault);
//        InternalConfigurations.SINGLE_WRITER_THREAD_POOL_SIZE.set(singleWriterThreadPoolSizeDefault);
//        InternalConfigurations.PERSISTENCE_STARTUP_THREAD_POOL_SIZE.set(persistenceStartupThreadPoolSizeDefault);
//    }

//    @SuppressWarnings("ResultOfMethodCallIgnored")
//    private File createHomeFolder() {
//        try {
//            final File createdFolder = File.createTempFile("junit", "");
//            createdFolder.delete();
//            createdFolder.mkdir();
//            System.setProperty(SystemProperties.HIVEMQ_HOME, createdFolder.getAbsolutePath());
//            return createdFolder;
//        } catch (final IOException e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
//
//    @SuppressWarnings("ResultOfMethodCallIgnored")
//    private void createConfigFile(final String configXML) {
//        try {
//            final File configFolder = new File(tempFolder, "conf");
//            configFolder.mkdir();
//            final File file = new File(configFolder, "config.xml");
//            file.createNewFile();
//            Files.write(configXML.getBytes(UTF_8), file);
//        } catch (final IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    @SuppressWarnings("ResultOfMethodCallIgnored")
//    private void recursiveDelete(final File file) {
//        final File[] files = file.listFiles();
//        if (files != null) {
//            for (final File each : files) {
//                recursiveDelete(each);
//            }
//        }
//        file.delete();
//    }

    private static class HiveMQServerException extends RuntimeException {

        private HiveMQServerException(final Throwable cause) {
            super(cause);
        }

        private static <V, T extends Throwable> @NotNull BiConsumer<V, T> handler(
                final @NotNull CompletableFuture<V> result) {
            return (value, throwable) -> {
                if (throwable != null) {
                    if (throwable instanceof HiveMQServerException) {
                        result.completeExceptionally(throwable.getCause());
                        return;
                    }
                    result.completeExceptionally(throwable);
                }
                result.complete(value);
            };
        }
    }
}
