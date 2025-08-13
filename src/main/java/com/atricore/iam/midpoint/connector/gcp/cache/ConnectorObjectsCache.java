package com.atricore.iam.midpoint.connector.gcp.cache;

import com.atricore.iam.midpoint.connector.gcp.GoogleCloudConfiguration;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.joda.time.Duration;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Cache of the connector objects retrieved from Google Apps Connector.
 *
 * @author oskar.butovic
 * @author jiri.vitinger
 */
public class ConnectorObjectsCache {

    private static Map<String, ConnectorObjectsCache> instances = new HashMap<>();

    private final Log logger;
    private final Map<String, ConnectorObjectWrapper> principalsMap;
    private final Map<String, ConnectorObjectWrapper> rolesMap;

    private boolean allowCache;
    private Duration maxCacheTTL;
    private Duration ignoreCacheAfterUpdateTTL;

    private GCPPolicyObject policy;
    private Long gcpPolicyTimeStamp = 0L;

    private ConnectorObjectsCache(GoogleCloudConfiguration configuration, Log connectorLogger) {
        principalsMap = new HashMap<>();
        rolesMap = new HashMap<>();
        logger = connectorLogger;

        configure(configuration);
        logger.ok("Cache() - created");
    }

    private void configure(GoogleCloudConfiguration configuration) {
        final boolean oldAllowCache = allowCache;
        final Duration oldMaxCacheTTL = maxCacheTTL;
        final Duration oldIgnoreCacheAfterUpdateTTL = ignoreCacheAfterUpdateTTL;

        maxCacheTTL = new Duration(configuration.getMaxCacheTTL());
        ignoreCacheAfterUpdateTTL = new Duration(configuration.getIgnoreCacheAfterUpdateTTL());
        allowCache = Boolean.TRUE.equals(configuration.getAllowCache());

        if (allowCache != oldAllowCache ||
                !maxCacheTTL.equals(oldMaxCacheTTL) ||
                !ignoreCacheAfterUpdateTTL.equals(oldIgnoreCacheAfterUpdateTTL)) {
            logger.ok("Cache() - configured with allowCache: " + allowCache +
                    ", maxCacheTTL: " + maxCacheTTL.getStandardSeconds() + " s" +
                    ", ignoreCacheAfterUpdateTTL: " + ignoreCacheAfterUpdateTTL.getStandardSeconds() + " s");
        }
    }

    public static ConnectorObjectsCache getInstance(GoogleCloudConfiguration configuration, Log connectorLogger) {

        ConnectorObjectsCache instance = instances.get(configuration.getProject());
        if (instance == null) {
            instance = new ConnectorObjectsCache(configuration, connectorLogger);
            instances.put(configuration.getProject(), instance);
        } else {
            instance.configure(configuration);
        }
        return instance;
    }

    /**
     * Returns the user from cache or null if there is none (or expired).
     */
    @Nullable
    public ConnectorObject getPrincipal(String uid) {
        return getObject(uid, ObjectType.PRINCIPAL);
    }

    public void removePrincipal(String uid) {
        removeObject(uid, ObjectType.PRINCIPAL);
    }

    public void addPrincipal(ConnectorObject user) {
        addObject(user, ObjectType.PRINCIPAL);
    }

    public void markPrincipalAsUpdatedNow(String uid) {
        markObjectAsUpdatedNow(uid, ObjectType.PRINCIPAL);
    }

    public GCPPolicyObject getPolicy() {
        if (!allowCache) {
            return null;
        }
        // Time between Policy timestamp and now exceeds TTL
        if ((System.currentTimeMillis() - this.gcpPolicyTimeStamp) > this.maxCacheTTL.getMillis()) {
            return null;
        }

        return this.policy;
    }

    public void setPolicy(GCPPolicyObject policy) {
        this.gcpPolicyTimeStamp = System.currentTimeMillis();
        this.policy = policy;
    }

    /**
     * Returns the group from cache or null if there is none (or expired).
     */
    @Nullable
    public ConnectorObject getRole(String uid) {
        return getObject(uid, ObjectType.ROLE);
    }

    public void removeRole(String uid) {
        removeObject(uid, ObjectType.ROLE);
    }

    public void addRole(ConnectorObject object) {
        addObject(object, ObjectType.ROLE);
    }

    public void markRoleAsUpdatedNow(String uid) {
        markObjectAsUpdatedNow(uid, ObjectType.ROLE);
    }

    @Nullable
    private ConnectorObject getObject(String uid, ObjectType type) {
        if (!allowCache) {
            return null;
        }
        removeExpiredObject(uid, type);
        ConnectorObjectWrapper objectWrapper = getMap(type).get(uid);
        if (objectWrapper != null) {
            if (objectWrapper.isIgnoredAfterUpdate(ignoreCacheAfterUpdateTTL)) {
                logger.ok("Cache.getObject() - " + type.name() + " - uid " + uid + " found but ignored after update, time added " + objectWrapper.getTimeAdded()
                        + ", time updated " + objectWrapper.getTimeUpdated());
                // In this case we don't want to return an object from cache but ignore the cache
                return null;
            } else {
                logger.ok("Cache.getObject() - " + type.name() + " - uid " + uid + " found, time added " + objectWrapper.getTimeAdded());
                return objectWrapper.getObject();
            }
        } else {
            logger.info("Cache.getObject() - " + type.name() + " - uid " + uid + " not found");
            return null;
        }
    }

    private void removeObject(String uid, ObjectType type) {
        if (!allowCache) {
            return;
        }
        if (getMap(type).remove(uid) != null) {
            logger.ok("Cache.removeObject() - " + type.name() + " - uid " + uid + " removed");
        } else {
            logger.warn("Cache.removeObject() - " + type.name() + " - uid " + uid + " not found in cache");
        }
    }

    private void addObject(ConnectorObject object, ObjectType type) {
        if (!allowCache) {
            return;
        }
        ConnectorObjectWrapper existingObjectWrapper = getMap(type).get(getUid(object));
        if (existingObjectWrapper != null && existingObjectWrapper.isIgnoredAfterUpdate(ignoreCacheAfterUpdateTTL)) {
            // In this case we don't want to add the object into the cache
            logger.ok("Cache.addObject() - " + type.name() + " - uid " + getUid(object) + " found but ignored after update, time added " + existingObjectWrapper.getTimeAdded()
                    + ", time updated " + existingObjectWrapper.getTimeUpdated());
            return;
        }
        ConnectorObjectWrapper objectWrapper = new ConnectorObjectWrapper(object);
        logger.ok("Cache.addObject() - " + type.name() + " - uid " + getUid(object) + ", time added " + objectWrapper.getTimeAdded());
        getMap(type).put(getUid(object), new ConnectorObjectWrapper(object));
    }

    private void markObjectAsUpdatedNow(String uid, ObjectType type) {
        if (!allowCache) {
            return;
        }
        ConnectorObjectWrapper objectWrapper = getMap(type).get(uid);
        if (objectWrapper != null) {
            objectWrapper.markAsUpdatedNow();
            logger.ok("Cache.markAsUpdatedNow() - " + type.name() + " - uid " + uid);
        }
    }

    private String getUid(ConnectorObject object) {
        return object.getUid().getUidValue();
    }

    /**
     * Removes old expired object from cache but keeps the object that is ignored in short period after update.
     */
    private void removeExpiredObject(String uid, ObjectType type) {
        ConnectorObjectWrapper objectWrapper = getMap(type).get(uid);
        if (objectWrapper != null &&
                objectWrapper.isExpired(maxCacheTTL, ignoreCacheAfterUpdateTTL) &&
                !objectWrapper.isIgnoredAfterUpdate(ignoreCacheAfterUpdateTTL)) {
            logger.ok("Cache.removeExpiredObject() - " + type.name() + " - uid " + uid + " expired, time added " + objectWrapper.getTimeAdded());
            removeObject(uid, type);
        }
    }

    private Map<String, ConnectorObjectWrapper> getMap(ObjectType objectType) {
        switch (objectType) {
            case PRINCIPAL:
                return principalsMap;
            case ROLE:
                return rolesMap;
            default:
                throw new IllegalArgumentException("Cache.getMap() - unknown object type: " + objectType);
        }
    }

    /**
     * Type of cached objects.
     */
    private enum ObjectType {
        PRINCIPAL,
        ROLE
    }
}
