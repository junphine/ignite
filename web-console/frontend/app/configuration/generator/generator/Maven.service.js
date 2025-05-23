

import _ from 'lodash';

import StringBuilder from './StringBuilder';
import ArtifactVersionChecker from './ArtifactVersionChecker.service';
import VersionService from 'app/services/Version.service';

// Pom dependency information.
import POM_DEPENDENCIES from 'app/data/pom-dependencies.json';

const versionService = new VersionService();

/**
 * Pom file generation entry point.
 */
export default class IgniteMavenGenerator {
    escapeId(s) {
        if (typeof (s) !== 'string')
            return s;

        return s.replace(/[^A-Za-z0-9_\-.]+/g, '_');
    }

    addProperty(sb, tag, val) {
        sb.append(`<${tag}>${val}</${tag}>`);
    }

    addComment(sb, comment) {
        sb.append(`<!-- ${comment} -->`);
    }

    addDependency(deps, groupId, artifactId, version, jar, link, exclude) {
        deps.push({groupId, artifactId, version, jar, link, exclude});
    }

    _extractVersion(igniteVer, version) {
        return _.isArray(version) ? _.find(version, (v) => versionService.since(igniteVer, v.range)).version : version;
    }

    pickDependency(acc, key, artifactGrp, dfltVer, igniteVer, storedVer) {
        const deps = POM_DEPENDENCIES[key];

        if (_.isNil(deps))
            return;

        _.forEach(_.castArray(deps), ({groupId, artifactId, version, jar, link, exclude}) => {
            this.addDependency(acc, groupId || artifactGrp, artifactId, storedVer || this._extractVersion(igniteVer, version) || dfltVer, jar, link, exclude);
        });
    }

    addResource(sb, dir, exclude) {
        sb.startBlock('<resource>');

        this.addProperty(sb, 'directory', dir);

        if (exclude) {
            sb.startBlock('<excludes>');
            this.addProperty(sb, 'exclude', exclude);
            sb.endBlock('</excludes>');
        }

        sb.endBlock('</resource>');
    }

    artifactSection(sb, artifactGrp, cluster, targetVer) {
        this.addProperty(sb, 'groupId', artifactGrp);
        this.addProperty(sb, 'artifactId', this.escapeId(cluster.name) + '-project');
        this.addProperty(sb, 'version', targetVer.ignite);
    }

    dependenciesSection(sb, deps) {
        sb.startBlock('<dependencies>');

        _.forEach(deps, (dep) => {
            sb.startBlock('<dependency>');

            this.addProperty(sb, 'groupId', dep.groupId);
            this.addProperty(sb, 'artifactId', dep.artifactId);
            this.addProperty(sb, 'version', dep.version);

            if (dep.jar) {
                this.addProperty(sb, 'scope', 'system');
                this.addProperty(sb, 'systemPath', '${project.basedir}/jdbc-drivers/' + dep.jar);
            }

            if (dep.link)
                this.addComment(sb, `You may download JDBC driver from: ${dep.link}`);

            if (dep.exclude) {
                sb.startBlock('<exclusions>');
                _.forEach(dep.exclude, (e) => {
                    sb.startBlock('<exclusion>');
                    this.addProperty(sb, 'groupId', e.groupId);
                    this.addProperty(sb, 'artifactId', e.artifactId);
                    sb.endBlock('</exclusion>');
                });
                sb.endBlock('</exclusions>');
            }

            sb.endBlock('</dependency>');
        });

        sb.endBlock('</dependencies>');

        return sb;
    }

    buildSection(sb = new StringBuilder(), excludeGroupIds) {
        sb.startBlock('<build>');

        sb.startBlock('<resources>');
        this.addResource(sb, 'src/main/java', '**/*.java');
        this.addResource(sb, 'src/main/resources');
        sb.endBlock('</resources>');

        sb.startBlock('<plugins>');

        sb.startBlock('<plugin>');
        this.addProperty(sb, 'artifactId', 'maven-dependency-plugin');
        sb.startBlock('<executions>');
        sb.startBlock('<execution>');
        this.addProperty(sb, 'id', 'copy-libs');
        this.addProperty(sb, 'phase', 'test-compile');
        sb.startBlock('<goals>');
        this.addProperty(sb, 'goal', 'copy-dependencies');
        sb.endBlock('</goals>');
        sb.startBlock('<configuration>');
        this.addProperty(sb, 'excludeGroupIds', excludeGroupIds.join(','));
        this.addProperty(sb, 'outputDirectory', 'target/libs');
        this.addProperty(sb, 'includeScope', 'compile');
        this.addProperty(sb, 'excludeTransitive', 'true');
        sb.endBlock('</configuration>');
        sb.endBlock('</execution>');
        sb.endBlock('</executions>');
        sb.endBlock('</plugin>');

        sb.startBlock('<plugin>');
        this.addProperty(sb, 'artifactId', 'maven-compiler-plugin');
        this.addProperty(sb, 'version', '3.8.1');
        sb.startBlock('<configuration>');
        this.addProperty(sb, 'source', '17');
        this.addProperty(sb, 'target', '17');
        sb.endBlock('</configuration>');
        sb.endBlock('</plugin>');

        sb.endBlock('</plugins>');

        sb.endBlock('</build>');
    }

    /**
     * Add dependency for specified store factory if not exist.
     *
     * @param artifactGrp Artifact group ID.
     * @param deps Already added dependencies.
     * @param storeFactory Store factory to add dependency.
     * @param igniteVer Ignite version.
     */
    storeFactoryDependency(artifactGrp, deps, storeFactory, igniteVer) {
        if (storeFactory.dialect && (!storeFactory.connectVia || storeFactory.connectVia === 'DataSource'))
            this.pickDependency(deps, storeFactory.dialect, artifactGrp, null, igniteVer, storeFactory.implementationVersion);
    }

    collectDependencies(artifactGrp, cluster, targetVer) {
        const igniteVer = '${ignite.version}';

        const deps = [];
        const storeDeps = [];

        this.addDependency(deps, artifactGrp, 'ignite-core', igniteVer);
        this.addDependency(deps, artifactGrp, 'ignite-igfs', igniteVer);
        this.addDependency(deps, artifactGrp, 'ignite-spring', igniteVer);
        this.addDependency(deps, artifactGrp, 'ignite-indexing', igniteVer);
        this.addDependency(deps, artifactGrp, 'ignite-vertx-rest', igniteVer);
        this.addDependency(deps, artifactGrp, 'ignite-web-console-common', igniteVer);        

        if (_.get(cluster, 'deploymentSpi.kind') === 'URI')
            this.addDependency(deps, artifactGrp, 'ignite-urideploy', igniteVer);

        if (_.get(cluster, 'crudui.kind') !== '')
            this.pickDependency(deps, 'ignite-crudui', artifactGrp, igniteVer);

        this.pickDependency(deps, cluster.discovery.kind, artifactGrp, igniteVer);

        const caches = cluster.caches;

        const blobStoreFactory = {cacheStoreFactory: {kind: 'CacheHibernateBlobStoreFactory'}};

        _.forEach(caches, (cache) => {
            if (cache.cacheStoreFactory && cache.cacheStoreFactory.kind)
                this.storeFactoryDependency(artifactGrp, storeDeps, cache.cacheStoreFactory[cache.cacheStoreFactory.kind], igniteVer);

            if (_.get(cache, 'nodeFilter.kind') === 'Exclude')
                this.addDependency(deps, artifactGrp, 'ignite-extdata-p2p', igniteVer);

            if (cache.diskPageCompression && versionService.since(igniteVer, '2.8.0'))
                this.addDependency(deps, artifactGrp, 'ignite-compress', igniteVer);
        });

        if (cluster.discovery.kind === 'Jdbc') {
            const store = cluster.discovery.Jdbc;

            if (store.dataSourceBean && store.dialect)
                this.storeFactoryDependency(artifactGrp, storeDeps, cluster.discovery.Jdbc, igniteVer);
        }

        _.forEach(cluster.checkpointSpi, (spi) => {
            if (spi.kind === 'S3')
                this.pickDependency(deps, spi.kind, artifactGrp, igniteVer);
            else if (spi.kind === 'JDBC')
                this.storeFactoryDependency(artifactGrp, storeDeps, spi.JDBC, igniteVer);
        });

        if (_.find(caches, blobStoreFactory))
            this.addDependency(deps, artifactGrp, 'ignite-hibernate', igniteVer);        

        return _.uniqWith(deps.concat(ArtifactVersionChecker.latestVersions(storeDeps)), _.isEqual);
    }

    /**
     * Generate pom.xml.
     *
     * @param {Object} cluster Cluster  to take info about dependencies.
     * @param {Object} targetVer Target version for dependencies.
     * @returns {String} Generated content.
     */
    generate(cluster, targetVer) {
        const sb = new StringBuilder();

        const artifactGrp = versionService.since(targetVer.ignite, '8.7.3')
            ? 'org.gridgain'
            : 'org.apache.ignite';

        sb.append('<?xml version="1.0" encoding="UTF-8"?>');

        sb.emptyLine();

        sb.append(`<!-- ${sb.generatedBy()} -->`);

        sb.emptyLine();

        sb.startBlock('<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">');

        sb.append('<modelVersion>4.0.0</modelVersion>');

        sb.emptyLine();

        this.artifactSection(sb, artifactGrp, cluster, targetVer);

        sb.emptyLine();

        const igniteVer = targetVer.ignite;
        sb.startBlock('<properties>');
        this.addProperty(sb, 'maven.compiler.source', '17');
        this.addProperty(sb, 'maven.compiler.target', '17');
        this.addProperty(sb, 'project.build.sourceEncoding', 'UTF-8');
        this.addProperty(sb, 'project.reporting.outputEncoding', 'UTF-8');
        this.addProperty(sb, 'ignite.version', igniteVer);
        sb.endBlock('</properties>');

        sb.emptyLine();

        const deps = this.collectDependencies(artifactGrp, cluster, targetVer);

        this.dependenciesSection(sb, deps);

        sb.emptyLine();

        this.buildSection(sb, [artifactGrp]);

        sb.endBlock('</project>');

        return sb.asString();
    }
}
