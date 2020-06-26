/**
 * Copyright 2010-2019 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.interactive_instruments.etf.bsxm;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;

import org.basex.query.QueryException;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;

/**
 * The GeometryManager is a spatial index and an in-memory cache for JTS geometries that can be used with the GmlGeoX module. The cache is filled during the indexing of the geometries and updated when geometries are accessed using the {@link GmlGeoX#getGeometry(Object, Object)} function.
 *
 * @author Clemens Portele (portele <at> interactive-instruments <dot> de)
 */
class GeometryManager {

    private static final Logger logger = LoggerFactory.getLogger(GeometryManager.class);

    // Max cache entries as number
    public static final String ETF_GEOCACHE_SIZE = "etf.gmlgeox.geocache.size";

    // Record hitcounts and misscounts as boolean
    public static final String ETF_GEOCACHE_REC_STATS = "etf.gmlgeox.geocache.statistics";

    private final Cache<String, Geometry> geometryCache;
    private RTree<IndexEntry, com.github.davidmoten.rtree.geometry.Geometry> rtree;

    GeometryManager() throws QueryException {
        this(Integer.valueOf(System.getProperty(ETF_GEOCACHE_SIZE, "100000")));
    }

    GeometryManager(final int maxSize) throws QueryException {
        try {
            if (logger.isDebugEnabled() ||
                    Boolean.valueOf(System.getProperty(ETF_GEOCACHE_REC_STATS, "false"))) {
                geometryCache = Caffeine.newBuilder().recordStats().maximumSize(maxSize).build();
            } else {
                geometryCache = Caffeine.newBuilder().maximumSize(maxSize).build();
            }
            rtree = RTree.star().create();
        } catch (Exception e) {
            throw new QueryException(
                    "Cache for geometries could not be initialized: " + e.getMessage());
        }
    }

    /**
     * Get feature geometry from the cache
     *
     * @param id
     *            the id for which the geometry should be retrieved, typically a gml:id of a GML feature element
     * @return the geometry of the indexed node, or null if no geometry was found
     */
    public org.locationtech.jts.geom.Geometry get(String id) {
        return geometryCache.getIfPresent(id);
    }

    /**
     * Returns the number of all read accesses to the cache
     *
     * @return number of read accesses to the cache
     */
    public long getCount() {
        return geometryCache.stats().hitCount();
    }

    /**
     * Returns the number of all failed read accesses to the cache
     *
     * @return number of failed read accesses to the cache
     */
    public long getMissCount() {
        return geometryCache.stats().missCount();
    }

    /**
     * Put a feature geometry in the cache
     *
     * @param id
     *            an id of the geometry, typically a gml:id of a GML feature element
     * @param geom
     *            the geometry to cache
     */
    public void put(String id, org.locationtech.jts.geom.Geometry geom) {
        geometryCache.put(id, geom);
    }

    /**
     * Index a geometry
     *
     * @param entry
     *            the index entry referencing the BaseX node
     * @param geometry
     *            the geometry to index
     */
    public void index(IndexEntry entry, com.github.davidmoten.rtree.geometry.Geometry geometry) {
        rtree = rtree.add(entry, geometry);
    }

    /**
     * Report current size of the spatial index
     *
     * @return size of the spatial index
     */
    public int indexSize() {
        return rtree.size();
    }

    /**
     * return all entries in the spatial index
     *
     * @return iterator over all entries
     */
    public Iterable<IndexEntry> search() {
        final Observable<Entry<IndexEntry, com.github.davidmoten.rtree.geometry.Geometry>> results = rtree.entries();
        return results.map(entry -> entry.value()).toBlocking().toIterable();
    }

    /**
     * return all entries in the spatial index that are in the bounding box
     *
     * @param bbox
     *            the bounding box / rectangle
     * @return iterator over all detected entries
     */
    public Iterable<IndexEntry> search(com.github.davidmoten.rtree.geometry.Rectangle bbox) {
        final Observable<Entry<IndexEntry, com.github.davidmoten.rtree.geometry.Geometry>> results = rtree.search(bbox);
        return results.map(entry -> entry.value()).toBlocking().toIterable();
    }
}
