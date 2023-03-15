/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkore.ignite.lucene.schema.mapping.builder;

import org.hawkore.ignite.lucene.schema.mapping.GeoPointMapper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@link MapperBuilder} to build a new {@link GeoPointMapper}.
 *
 * @author Andres de la Pena {@literal <adelapena@stratio.com>}
 */
public class GeoPointMapperBuilder extends MapperBuilder<GeoPointMapper, GeoPointMapperBuilder> {

    /** The name of the column containing the latitude. */
    @JsonProperty("latitude")
    private final String latitude;

    /** The name of the column containing the longitude. */
    @JsonProperty("longitude")
    private final String longitude;

    /** The maximum number of levels in the tree. */
    @JsonProperty("max_levels")
    private Integer maxLevels;

    /**
     * Builds a new {@link GeoPointMapper}.
     *
     * @param latitude the name of the column containing the latitude
     * @param longitude the name of the column containing the longitude
     */
    @JsonCreator
    public GeoPointMapperBuilder(@JsonProperty("latitude") String latitude,
                                 @JsonProperty("longitude") String longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Sets the maximum number of levels in the tree.
     *
     * @param maxLevels the maximum number of levels in the tree
     * @return this
     */
    public GeoPointMapperBuilder maxLevels(Integer maxLevels) {
        this.maxLevels = maxLevels;
        return this;
    }

    /**
     * Returns the {@link GeoPointMapper} represented by this {@link MapperBuilder}.
     *
     * @param field the name of the field to be built
     * @return the {@link GeoPointMapper} represented by this
     */
    @Override
    public GeoPointMapper build(String field) {
        return new GeoPointMapper(field, validated, latitude, longitude, maxLevels);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((latitude == null) ? 0 : latitude.hashCode());
        result = prime * result + ((longitude == null) ? 0 : longitude.hashCode());
        result = prime * result + ((maxLevels == null) ? 0 : maxLevels.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        GeoPointMapperBuilder other = (GeoPointMapperBuilder) obj;
        if (latitude == null) {
            if (other.latitude != null)
                return false;
        } else if (!latitude.equals(other.latitude))
            return false;
        if (longitude == null) {
            if (other.longitude != null)
                return false;
        } else if (!longitude.equals(other.longitude))
            return false;
        if (maxLevels == null) {
            if (other.maxLevels != null)
                return false;
        } else if (!maxLevels.equals(other.maxLevels))
            return false;
        return true;
    }
 }
