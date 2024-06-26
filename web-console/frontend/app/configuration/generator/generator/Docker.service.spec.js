

import DockerGenerator from './Docker.service';
import {assert} from 'chai';
import {outdent} from 'outdent/lib';

suite('Dockerfile generator', () => {
    const generator = new DockerGenerator();

    test('Target 2.0', () => {
        const cluster = {
            name: 'FooBar'
        };

        const version = {ignite: '2.0.0'};

        assert.equal(
            generator.generate(cluster, version),
            outdent`
                # Start from Apache Ignite image.',
                FROM apacheignite/ignite:2.0.0

                # Set config uri for node.
                ENV CONFIG_URI FooBar-server.xml

                # Copy optional libs.
                ENV OPTION_LIBS ignite-rest-http

                # Update packages and install maven.
                RUN \\
                   apt-get update &&\\
                   apt-get install -y maven

                # Append project to container.
                ADD . FooBar

                # Build project in container.
                RUN mvn -f FooBar/pom.xml clean package -DskipTests

                # Copy project jars to node classpath.
                RUN mkdir $IGNITE_HOME/libs/FooBar && \\
                   find FooBar/target -name "*.jar" -type f -exec cp {} $IGNITE_HOME/libs/FooBar \\;
            `
        );
    });
    test('Target 2.1', () => {
        const cluster = {
            name: 'FooBar'
        };
        const version = {ignite: '2.1.0'};
        assert.equal(
            generator.generate(cluster, version),
            outdent`
                # Start from Apache Ignite image.',
                FROM apacheignite/ignite:2.1.0

                # Set config uri for node.
                ENV CONFIG_URI FooBar-server.xml

                # Copy optional libs.
                ENV OPTION_LIBS ignite-rest-http

                # Update packages and install maven.
                RUN set -x \\
                    && apk add --no-cache \\
                        openjdk8

                RUN apk --update add \\
                    maven \\
                    && rm -rfv /var/cache/apk/*

                # Append project to container.
                ADD . FooBar

                # Build project in container.
                RUN mvn -f FooBar/pom.xml clean package -DskipTests

                # Copy project jars to node classpath.
                RUN mkdir $IGNITE_HOME/libs/FooBar && \\
                   find FooBar/target -name "*.jar" -type f -exec cp {} $IGNITE_HOME/libs/FooBar \\;
            `
        );
    });

    test('Discovery optional libs', () => {
        const generateWithDiscovery = (discovery) => generator.generate({name: 'foo', discovery: {kind: discovery}}, {ignite: '2.1.0'});

        assert.include(
            generateWithDiscovery('Cloud'),
            `ENV OPTION_LIBS ignite-rest-http,ignite-cloud`,
            'Adds Apache jclouds lib'
        );

        assert.include(
            generateWithDiscovery('S3'),
            `ENV OPTION_LIBS ignite-rest-http,ignite-aws`,
            'Adds Amazon AWS lib'
        );

        assert.include(
            generateWithDiscovery('GoogleStorage'),
            `ENV OPTION_LIBS ignite-rest-http,ignite-gce`,
            'Adds Google Cloud Engine lib'
        );

        assert.include(
            generateWithDiscovery('ZooKeeper'),
            `ENV OPTION_LIBS ignite-rest-http,ignite-zookeeper`,
            'Adds Zookeeper lib'
        );

        assert.include(
            generateWithDiscovery('Kubernetes'),
            `ENV OPTION_LIBS ignite-rest-http,ignite-kubernetes`,
            'Adds Kubernetes lib'
        );
    });
});
