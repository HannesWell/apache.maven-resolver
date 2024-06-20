/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.supplier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.DefaultModelCacheFactory;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.apache.maven.repository.internal.ModelCacheFactory;
import org.apache.maven.repository.internal.PluginsMetadataGeneratorFactory;
import org.apache.maven.repository.internal.SnapshotMetadataGeneratorFactory;
import org.apache.maven.repository.internal.VersionsMetadataGeneratorFactory;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.impl.supplier.BasicRepositorySystemSupplier;
import org.eclipse.aether.spi.checksums.ProvidedChecksumsSource;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.ChecksumExtractor;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.transport.http.Nexus2ChecksumExtractor;
import org.eclipse.aether.transport.http.XChecksumChecksumExtractor;

/**
 * A simple {@link Supplier} of {@link org.eclipse.aether.RepositorySystem} instances, that on each call supplies newly
 * constructed instance. For proper shut down, use {@link RepositorySystem#shutdown()} method on supplied instance(s).
 * <p>
 * Extend this class and override methods to customize, if needed.
 *
 * @since 1.9.15
 */
public class RepositorySystemSupplier extends BasicRepositorySystemSupplier {

    @Override
    protected Map<String, TransporterFactory> getTransporterFactories() {
        Map<String, ChecksumExtractor> checksumExtractors = getChecksumExtractors();
        return getTransporterFactories(checksumExtractors);
    }

    protected Map<String, ChecksumExtractor> getChecksumExtractors() {
        HashMap<String, ChecksumExtractor> result = new HashMap<>();
        result.put(Nexus2ChecksumExtractor.NAME, new Nexus2ChecksumExtractor());
        result.put(XChecksumChecksumExtractor.NAME, new XChecksumChecksumExtractor());
        return result;
    }

    protected Map<String, TransporterFactory> getTransporterFactories(Map<String, ChecksumExtractor> extractors) {
        HashMap<String, TransporterFactory> result = new HashMap<>();
        result.put(FileTransporterFactory.NAME, new FileTransporterFactory());
        result.put(HttpTransporterFactory.NAME, new HttpTransporterFactory(extractors));
        return result;
    }

    @Override
    protected Map<String, RepositoryConnectorFactory> getRepositoryConnectorFactories(
            TransporterProvider transporterProvider,
            RepositoryLayoutProvider repositoryLayoutProvider,
            ChecksumPolicyProvider checksumPolicyProvider,
            FileProcessor fileProcessor,
            Map<String, ProvidedChecksumsSource> providedChecksumsSources) {
        BasicRepositoryConnectorFactory basic = getBasicRepositoryConnectorFactory(
                transporterProvider,
                repositoryLayoutProvider,
                checksumPolicyProvider,
                fileProcessor,
                providedChecksumsSources);
        return getRepositoryConnectorFactories(basic);
    }

    protected BasicRepositoryConnectorFactory getBasicRepositoryConnectorFactory(
            TransporterProvider transporterProvider,
            RepositoryLayoutProvider repositoryLayoutProvider,
            ChecksumPolicyProvider checksumPolicyProvider,
            FileProcessor fileProcessor,
            Map<String, ProvidedChecksumsSource> providedChecksumsSources) {
        return new BasicRepositoryConnectorFactory(
                transporterProvider,
                repositoryLayoutProvider,
                checksumPolicyProvider,
                fileProcessor,
                providedChecksumsSources);
    }

    protected Map<String, RepositoryConnectorFactory> getRepositoryConnectorFactories(
            BasicRepositoryConnectorFactory basicRepositoryConnectorFactory) {
        HashMap<String, RepositoryConnectorFactory> result = new HashMap<>();
        result.put(BasicRepositoryConnectorFactory.NAME, basicRepositoryConnectorFactory);
        return result;
    }

    @Override
    protected Map<String, MetadataGeneratorFactory> getMetadataGeneratorFactories() {
        // from maven-resolver-provider
        HashMap<String, MetadataGeneratorFactory> result = new HashMap<>();
        result.put(PluginsMetadataGeneratorFactory.NAME, new PluginsMetadataGeneratorFactory());
        result.put(VersionsMetadataGeneratorFactory.NAME, new VersionsMetadataGeneratorFactory());
        result.put(SnapshotMetadataGeneratorFactory.NAME, new SnapshotMetadataGeneratorFactory());
        return result;
    }

    @Override
    protected ArtifactDescriptorReader getArtifactDescriptorReader(
            RemoteRepositoryManager remoteRepositoryManager,
            VersionResolver versionResolver,
            VersionRangeResolver versionRangeResolver,
            ArtifactResolver artifactResolver,
            RepositoryEventDispatcher repositoryEventDispatcher) {
        ModelBuilder modelBuilder = getModelBuilder();
        ModelCacheFactory modelCacheFactory = getModelCacheFactory();
        return getArtifactDescriptorReader(
                remoteRepositoryManager,
                versionResolver,
                versionRangeResolver,
                artifactResolver,
                modelBuilder,
                repositoryEventDispatcher,
                modelCacheFactory);
    }

    protected ArtifactDescriptorReader getArtifactDescriptorReader(
            RemoteRepositoryManager remoteRepositoryManager,
            VersionResolver versionResolver,
            VersionRangeResolver versionRangeResolver,
            ArtifactResolver artifactResolver,
            ModelBuilder modelBuilder,
            RepositoryEventDispatcher repositoryEventDispatcher,
            ModelCacheFactory modelCacheFactory) {
        // from maven-resolver-provider
        DefaultArtifactDescriptorReader result = new DefaultArtifactDescriptorReader();
        result.setRemoteRepositoryManager(remoteRepositoryManager);
        result.setVersionResolver(versionResolver);
        result.setVersionRangeResolver(versionRangeResolver);
        result.setArtifactResolver(artifactResolver);
        result.setModelBuilder(modelBuilder);
        result.setRepositoryEventDispatcher(repositoryEventDispatcher);
        result.setModelCacheFactory(modelCacheFactory);
        return result;
    }

    @Override
    protected VersionResolver getVersionResolver(
            MetadataResolver metadataResolver,
            SyncContextFactory syncContextFactory,
            RepositoryEventDispatcher repositoryEventDispatcher) {
        // from maven-resolver-provider
        DefaultVersionResolver result = new DefaultVersionResolver();
        result.setMetadataResolver(metadataResolver);
        result.setSyncContextFactory(syncContextFactory);
        result.setRepositoryEventDispatcher(repositoryEventDispatcher);
        return result;
    }

    @Override
    protected VersionRangeResolver getVersionRangeResolver(
            MetadataResolver metadataResolver,
            SyncContextFactory syncContextFactory,
            RepositoryEventDispatcher repositoryEventDispatcher) {
        // from maven-resolver-provider
        DefaultVersionRangeResolver result = new DefaultVersionRangeResolver();
        result.setMetadataResolver(metadataResolver);
        result.setSyncContextFactory(syncContextFactory);
        result.setRepositoryEventDispatcher(repositoryEventDispatcher);
        return result;
    }

    protected ModelBuilder getModelBuilder() {
        // from maven-model-builder
        return new DefaultModelBuilderFactory().newInstance();
    }

    protected ModelCacheFactory getModelCacheFactory() {
        // from maven-resolver-provider
        return new DefaultModelCacheFactory();
    }
}
