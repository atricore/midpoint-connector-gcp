package com.atricore.iam.midpoint.connector.gcp;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.identityconnectors.framework.spi.StatefulConfiguration;

public class GoogleCloudConfiguration extends AbstractConfiguration implements StatefulConfiguration {

    private Long orgId = null;
    private String project = null;
    /**
     * Client secret or {@code null} for none.
     */
    private GuardedString serviceAccountKeyJson = null;

    private boolean backupPolicy;
    private String backupPolicyLocation;

    /**
     * caching
     */
    private Long maxCacheTTL = 300000L;
    private Long ignoreCacheAfterUpdateTTL = 5000L;
    private Boolean allowCache;


    @ConfigurationProperty(order = 1, displayMessageKey = "orgId.display",
            groupMessageKey = "basic.group", helpMessageKey = "orgId.help", required = false,
            confidential = false)
    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    @ConfigurationProperty(order = 1, displayMessageKey = "project.display",
            groupMessageKey = "basic.group", helpMessageKey = "project.help", required = true,
            confidential = false)
    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    @ConfigurationProperty(order = 2, displayMessageKey = "serviceAccountKeyJson.display",
            groupMessageKey = "basic.group", helpMessageKey = "serviceAccountKeyJson.help", required = true,
            confidential = true)
    public GuardedString getServiceAccountKeyJson() {
        return serviceAccountKeyJson;
    }

    @ConfigurationProperty(order = 3, displayMessageKey = "backupPolicy.display",
            groupMessageKey = "basic.group", helpMessageKey = "backupPolicy.help", required = true,
            confidential = true)
    public boolean isBackupPolicy() {
        return backupPolicy;
    }

    public void setBackupPolicy(boolean backupPolicy) {
        this.backupPolicy = backupPolicy;
    }

    @ConfigurationProperty(order = 4, displayMessageKey = "backupPolicyLocation.display",
            groupMessageKey = "basic.group", helpMessageKey = "backupPolicyLocation.help", required = true,
            confidential = true)
    public String getBackupPolicyLocation() {
        return backupPolicyLocation;
    }

    public void setBackupPolicyLocation(String backupPolicyLocation) {
        this.backupPolicyLocation = backupPolicyLocation;
    }

    @ConfigurationProperty(order = 10, displayMessageKey = "allowCache.display",
            groupMessageKey = "basic.group", helpMessageKey = "allowCache.help", required = true,
            confidential = false)
    public Boolean getAllowCache() {
        return allowCache;
    }

    public void setAllowCache(Boolean allowCache) {
        this.allowCache = allowCache;
    }

    @ConfigurationProperty(order = 11, displayMessageKey = "maxCacheTTL.display",
            groupMessageKey = "basic.group", helpMessageKey = "maxCacheTTL.help", required = true,
            confidential = false)
    public Long getMaxCacheTTL() {
        return maxCacheTTL;
    }

    public void setMaxCacheTTL(Long maxCacheTTL) {
        this.maxCacheTTL = maxCacheTTL;
    }

    @ConfigurationProperty(order = 12, displayMessageKey = "ignoreCacheAfterUpdateTTL.display",
            groupMessageKey = "basic.group", helpMessageKey = "ignoreCacheAfterUpdateTTL.help", required = true,
            confidential = false)
    public Long getIgnoreCacheAfterUpdateTTL() {
        return ignoreCacheAfterUpdateTTL;
    }

    public void setIgnoreCacheAfterUpdateTTL(Long ignoreCacheAfterUpdateTTL) {
        this.ignoreCacheAfterUpdateTTL = ignoreCacheAfterUpdateTTL;
    }

    public void setServiceAccountKeyJson(GuardedString clientSecret) {
        this.serviceAccountKeyJson = clientSecret;
    }

    @Override
    public void validate() {
        if (StringUtil.isBlank(project)) {
            throw new IllegalArgumentException("Project cannot be null or empty.");
        }
        if (StringUtil.isBlank(serviceAccountKeyJson.toString())) {
            throw new IllegalArgumentException("Service key cannot be null or empty.");
        }
    }

    @Override
    public void release() {

    }
}
