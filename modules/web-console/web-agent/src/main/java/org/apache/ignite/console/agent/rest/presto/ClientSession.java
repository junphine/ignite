/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ignite.console.agent.rest.presto;


import java.net.URI;
import java.nio.charset.CharsetEncoder;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import java.util.Set;

import com.beust.jcommander.internal.Maps;
import com.beust.jcommander.internal.Sets;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;


public class ClientSession
{
    private final URI server;
    private final String user;
    private final String source;
    private final Optional<String> traceToken;
    private final Set<String> clientTags;
    private final String clientInfo;
    private final String catalog;
    private final String schema;
    private final Map<String, String> roles;
    private final Locale locale;
    private final Map<String, String> resourceEstimates;
    private final Map<String, String> properties;
    private final Map<String, String> preparedStatements;
  
    private final Map<String, String> extraCredentials;
    private final String transactionId;
    private final Duration clientRequestTimeout;
    private final String timeZone;
    
    private final OkHttpClient.Builder httpBuilder;
    
    // catalog,schemas, tables
    Map<String,Map<String,ObjectNode>> metasMap = new HashMap<>();
    
    public static Builder builder(ClientSession clientSession)
    {
        return new Builder(clientSession);
    }

    public static ClientSession stripTransactionId(ClientSession session)
    {
        return ClientSession.builder(session)
                .withoutTransactionId()
                .build();
    }
    
    public ClientSession(
            URI server,
            String user,
            String source,
            String catalog,
            String schema
           )
    {
    	this(server,user,source,Optional.empty(),Sets.newHashSet(),null,catalog,schema,
    			null,null,Maps.newHashMap(),Maps.newHashMap(),Maps.newHashMap(),Maps.newHashMap(),Maps.newHashMap(),
    			null,Duration.ofHours(24)
    			);
    }

    public ClientSession(
            URI server,
            String user,
            String source,
            Optional<String> traceToken,
            Set<String> clientTags,
            String clientInfo,
            String catalog,
            String schema,
            String timeZoneId,
            Locale locale,
            Map<String, String> resourceEstimates,
            Map<String, String> properties,
            Map<String, String> preparedStatements,
            Map<String, String> roles,
            Map<String, String> extraCredentials,
            String transactionId,
            Duration clientRequestTimeout)
    {
        this.server = requireNonNull(server, "server is null");
        this.user = user;
        this.source = source;
        this.traceToken = requireNonNull(traceToken, "traceToken is null");
        this.clientTags = ImmutableSet.copyOf(requireNonNull(clientTags, "clientTags is null"));
        this.clientInfo = clientInfo;
        this.catalog = catalog;
        this.schema = schema;
        this.locale = locale;
        this.timeZone = timeZoneId;
        this.transactionId = transactionId;
        this.resourceEstimates = ImmutableMap.copyOf(requireNonNull(resourceEstimates, "resourceEstimates is null"));
        this.properties = ImmutableMap.copyOf(requireNonNull(properties, "properties is null"));
        this.preparedStatements = ImmutableMap.copyOf(requireNonNull(preparedStatements, "preparedStatements is null"));
        this.roles = ImmutableMap.copyOf(requireNonNull(roles, "roles is null"));
        this.extraCredentials = ImmutableMap.copyOf(requireNonNull(extraCredentials, "extraCredentials is null"));
        this.clientRequestTimeout = clientRequestTimeout;

        for (String clientTag : clientTags) {
            checkArgument(!clientTag.contains(","), "client tag cannot contain ','");
        }

        // verify that resource estimates are valid
        CharsetEncoder charsetEncoder = US_ASCII.newEncoder();
        for (Entry<String, String> entry : resourceEstimates.entrySet()) {
            checkArgument(!entry.getKey().isEmpty(), "Resource name is empty");
            checkArgument(entry.getKey().indexOf('=') < 0, "Resource name must not contain '=': %s", entry.getKey());
            checkArgument(charsetEncoder.canEncode(entry.getKey()), "Resource name is not US_ASCII: %s", entry.getKey());
        }

        // verify the properties are valid
        for (Entry<String, String> entry : properties.entrySet()) {
            checkArgument(!entry.getKey().isEmpty(), "Session property name is empty");
            checkArgument(entry.getKey().indexOf('=') < 0, "Session property name must not contain '=': %s", entry.getKey());
            checkArgument(charsetEncoder.canEncode(entry.getKey()), "Session property name is not US_ASCII: %s", entry.getKey());
            checkArgument(charsetEncoder.canEncode(entry.getValue()), "Session property value is not US_ASCII: %s", entry.getValue());
        }

        // verify the extra credentials are valid
        for (Entry<String, String> entry : extraCredentials.entrySet()) {
            checkArgument(!entry.getKey().isEmpty(), "Credential name is empty");
            checkArgument(entry.getKey().indexOf('=') < 0, "Credential name must not contain '=': %s", entry.getKey());
            checkArgument(charsetEncoder.canEncode(entry.getKey()), "Credential name is not US_ASCII: %s", entry.getKey());
            checkArgument(charsetEncoder.canEncode(entry.getValue()), "Credential value is not US_ASCII: %s", entry.getValue());
        }
        
        Dispatcher dispatcher = new Dispatcher();

        dispatcher.setMaxRequests(Integer.MAX_VALUE);
        dispatcher.setMaxRequestsPerHost(Integer.MAX_VALUE);

        httpBuilder = new OkHttpClient.Builder()
            .readTimeout(clientRequestTimeout.getSeconds(), TimeUnit.SECONDS)
            .dispatcher(dispatcher);  
    }

    public URI getServer()
    {
        return server;
    }

    public String getUser()
    {
        return user;
    }

    public String getSource()
    {
        return source;
    }

    public Optional<String> getTraceToken()
    {
        return traceToken;
    }

    public Set<String> getClientTags()
    {
        return clientTags;
    }

    public String getClientInfo()
    {
        return clientInfo;
    }

    public String getCatalog()
    {
        return catalog;
    }

    public String getSchema()
    {
        return schema;
    }

    public String getTimeZone()
    {
        return this.timeZone;
    }

    public Locale getLocale()
    {
        return locale;
    }

    public Map<String, String> getResourceEstimates()
    {
        return resourceEstimates;
    }

    public Map<String, String> getProperties()
    {
        return properties;
    }

    public Map<String, String> getPreparedStatements()
    {
        return preparedStatements;
    }

    /**
     * Returns the map of catalog name -> selected role
     */
    public Map<String, String> getRoles()
    {
        return roles;
    }

    public Map<String, String> getExtraCredentials()
    {
        return extraCredentials;
    }

    public String getTransactionId()
    {
        return transactionId;
    }

    public boolean isDebug()
    {
        return false;
    }

    public Duration getClientRequestTimeout()
    {
        return clientRequestTimeout;
    }
    
    public PrestoClient newPrestoClient() {
    	
    	PrestoClient client = new PrestoClient(this.httpBuilder.build(),this);
    	return client;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("server", server)
                .add("user", user)
                .add("clientTags", clientTags)
                .add("clientInfo", clientInfo)
                .add("catalog", catalog)
                .add("schema", schema)
                .add("traceToken", traceToken.orElse(null))
                .add("timeZone", timeZone)
                .add("locale", locale)
                .add("properties", properties)
                .add("transactionId", transactionId)
                .omitNullValues()
                .toString();
    }

    public static final class Builder
    {
        private URI server;
        private String user;
        private String source;
        private Optional<String> traceToken;
        private Set<String> clientTags;
        private String clientInfo;
        private String catalog;
        private String schema;
        private String timeZone;
        private Locale locale;
        private Map<String, String> resourceEstimates;
        private Map<String, String> properties;
        private Map<String, String> preparedStatements;
        private Map<String, String> roles;
        private Map<String, String> credentials;
        private String transactionId;
        private Duration clientRequestTimeout;

        private Builder(ClientSession clientSession)
        {
            requireNonNull(clientSession, "clientSession is null");
            server = clientSession.getServer();
            user = clientSession.getUser();
            source = clientSession.getSource();
            traceToken = clientSession.getTraceToken();
            clientTags = clientSession.getClientTags();
            clientInfo = clientSession.getClientInfo();
            catalog = clientSession.getCatalog();
            schema = clientSession.getSchema();
            timeZone = clientSession.getTimeZone();
            locale = clientSession.getLocale();
            resourceEstimates = clientSession.getResourceEstimates();
            properties = clientSession.getProperties();
            preparedStatements = clientSession.getPreparedStatements();
            roles = clientSession.getRoles();
            credentials = clientSession.getExtraCredentials();
            transactionId = clientSession.getTransactionId();
            clientRequestTimeout = clientSession.getClientRequestTimeout();
        }

        public Builder withCatalog(String catalog)
        {
            this.catalog = requireNonNull(catalog, "catalog is null");
            return this;
        }

        public Builder withSchema(String schema)
        {
            this.schema = requireNonNull(schema, "schema is null");
            return this;
        }

        public Builder withProperties(Map<String, String> properties)
        {
            this.properties = requireNonNull(properties, "properties is null");
            return this;
        }

        public Builder withRoles(Map<String, String> roles)
        {
            this.roles = roles;
            return this;
        }

        public Builder withCredentials(Map<String, String> credentials)
        {
            this.credentials = requireNonNull(credentials, "extraCredentials is null");
            return this;
        }

        public Builder withPreparedStatements(Map<String, String> preparedStatements)
        {
            this.preparedStatements = requireNonNull(preparedStatements, "preparedStatements is null");
            return this;
        }

        public Builder withTransactionId(String transactionId)
        {
            this.transactionId = requireNonNull(transactionId, "transactionId is null");
            return this;
        }

        public Builder withoutTransactionId()
        {
            this.transactionId = null;
            return this;
        }

        public ClientSession build()
        {
            return new ClientSession(
                    server,
                    user,
                    source,
                    traceToken,
                    clientTags,
                    clientInfo,
                    catalog,
                    schema,
                    timeZone,
                    locale,
                    resourceEstimates,
                    properties,
                    preparedStatements,
                    roles,
                    credentials,
                    transactionId,
                    clientRequestTimeout);
        }
    }
}
