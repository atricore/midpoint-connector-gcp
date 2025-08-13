package com.atricore.iam.midpoint.connector.gcp.handlers;

import com.atricore.iam.midpoint.connector.gcp.cache.ConnectorObjectsCache;
import com.atricore.iam.midpoint.connector.gcp.cache.GCPPolicyObject;
import com.atricore.iam.midpoint.connector.gcp.cache.RoleBinding;
import com.google.cloud.iam.admin.v1.IAMClient;
import com.google.iam.admin.v1.GetRoleRequest;
import com.google.iam.admin.v1.ListRolesRequest;
import com.google.iam.admin.v1.Role;
import com.google.iam.admin.v1.RoleView;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.*;
import org.identityconnectors.framework.spi.SearchResultsHandler;

import java.util.Set;

import static com.atricore.iam.midpoint.connector.gcp.GoogleCloudConnector.*;

public class RoleHandler implements FilterVisitor<Void, ListRolesRequest.Builder>{

    private static final Log logger = Log.getLog(RoleHandler.class);

    private ConnectorObjectsCache objectsCache;
    private IAMClient client;
    private String project;
    private Long organization;

    public RoleHandler(Long orgId, String project, ConnectorObjectsCache objectsCache, IAMClient client) {
        this.project = project;
        this.organization = orgId;
        this.objectsCache = objectsCache;
        this.client = client;
    }

    public static ObjectClassInfo getClassInfo() {
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType("GCPRole");

        builder.addAttributeInfo(AttributeInfoBuilder.define(Name.NAME)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(TITLE_ATTR)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(PROJECT_ATTR)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(DESCRIPTION_ATTR)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(INCLUDED_PERMISSIONS_COUNT_ATTR)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(INCLUDED_PERMISSIONS_LIST_ATTR)
                .setUpdateable(false).setCreateable(false).setMultiValued(true).build());

        //AttributeInfo GROUPS = AttributeInfoBuilder.build( PredefinedAttributes.GROUPS_NAME, String.class, EnumSet.of(AttributeInfo.Flags.MULTIVALUED));
        //builder.addAttributeInfo(GROUPS);

        builder.addAttributeInfo(AttributeInfoBuilder.define(USER_MEMBERS_ATTR).setMultiValued(true)
                .setReturnedByDefault(true).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(GROUP_MEMBERS_ATTR).setMultiValued(true)
                .setReturnedByDefault(true).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(SERVICE_ACCOUNT_MEMBERS_ATTR).setMultiValued(true)
                .setReturnedByDefault(true).build());

        return builder.build();
    }

    // ---------------------------------------------------------------------------------
    // Role queries
    public void executeRoleReadQuery(GCPPolicyObject p,
                                     Uid uid,
                                     final ResultsHandler handler,
                                     OperationOptions options,
                                     final Set<String> attributesToGet) {
        try {
            // Try the cache first
            ConnectorObject cachedRole = objectsCache.getRole(uid.getUidValue());
            if (cachedRole != null) {
                handler.handle(cachedRole);
                return;
            }

            // This will read all roles: standard, prj custom, org custom since the uid and name contains the parent
            logger.info("Looking for role (byUid): ["+uid.getUidValue()+"] ");
            GetRoleRequest req = GetRoleRequest.newBuilder().setName(uid.getUidValue()).build();
            Role value = client.getRole(req);

            ConnectorObject role = fromRole(value, p, attributesToGet);
            objectsCache.addRole(role);
            handler.handle(role);

        } catch (Exception e) {
            logger.warn(e, "Failed to read GCP role byUid: ["+uid.getUidValue()+"] ");
            throw ConnectorException.wrap(e);
        }
    }

    public void executeRoleReadQuery(GCPPolicyObject p,
                                     Name name,
                                     final ResultsHandler handler,
                                     OperationOptions options,
                                     final Set<String> attributesToGet) {
        try {
            // Try the cache first
            ConnectorObject cachedRole = objectsCache.getRole(name.getNameValue());
            if (cachedRole != null) {
                handler.handle(cachedRole);
                return;
            }

            String sName = name.getNameValue();
            logger.info("Looking for role (byName): ["+name.getNameValue()+":"+sName+"] ");
            GetRoleRequest req = GetRoleRequest.newBuilder().setName(sName).build();
            Role value = client.getRole(req);
            logger.info("Found role (byName) [" + value.getName() + " ] for name [" + sName+"] ");

            ConnectorObject role = fromRole(value, p, attributesToGet);
            objectsCache.addRole(role);
            handler.handle(role);

        } catch (Exception e) {
            logger.warn(e, "Failed to read GCP role byName: ["+name.getNameValue()+"] ");
            throw ConnectorException.wrap(e);
        }
    }


    public void executeRoleSearchQuery(GCPPolicyObject p,
                                       Filter query,
                                       final ResultsHandler handler,
                                       OperationOptions options, final Set<String> attributesToGet) {

        // We must read custom roles as well, these are under projects/<project-id> and organizations/<org-id>

        // sName can start with:
        // - roles/ for standard roles
        // - projects/ for project roles
        // - organizations / for organization roles

        // read standard built-in roles
        executeRoleSearchByParentQuery(p, "", query , handler, options, attributesToGet);

        // read project custom roles
        executeRoleSearchByParentQuery(p, "projects/" + this.project, query , handler, options, attributesToGet);

        // read org custom roles, if org is configured
        if (this.organization > 0)
            executeRoleSearchByParentQuery(p, "organizations/" + this.organization, query , handler, options, attributesToGet);

    }

    /**
     * sName can start with:
     *  - roles/ for standard roles
     *  - projects/ for project roles
     *  - organizations / for organization roles
     *
     * @param parent either emtpy string, a project (projects/<prj>) or an orgId (/organizations/<orgId>
     */
    protected void executeRoleSearchByParentQuery(GCPPolicyObject p,
                                       String parent,
                                       Filter query,
                                       final ResultsHandler handler,
                                       OperationOptions options, final Set<String> attributesToGet) {
        try {
            ListRolesRequest.Builder builder = ListRolesRequest.newBuilder().setParent(parent).setView(RoleView.FULL);
            if (null != query) {
                query.accept(this, builder);
            }

            boolean paged = false;
            // Groups
            if (null != options.getPageSize() && options.getPageSize() >= 1 && options.getPageSize() <= 500) {
                builder.setPageSize(options.getPageSize());
                paged = true;
            }

            String nextPageToken = null;
            do {
                if (StringUtil.isNotBlank(nextPageToken)) {
                    builder.setPageToken(nextPageToken);
                }

                ListRolesRequest req = builder.build();
                IAMClient.ListRolesPagedResponse page = client.listRoles(req);
                for (Role role : page.getPage().getValues()) {
                    handler.handle(fromRole(role, p, attributesToGet));
                }

                nextPageToken = page.getNextPageToken();

                if (StringUtil.isNotBlank(nextPageToken))
                    builder.setPageToken(nextPageToken);
            } while (!paged && StringUtil.isNotBlank(nextPageToken));

            if (paged && StringUtil.isNotBlank(nextPageToken)) {
                logger.info("Paged Search was requested");
                ((SearchResultsHandler) handler).handleResult(new SearchResult(
                        nextPageToken, 0));
            }

        } catch (Exception e) {
            logger.warn(e, "Failed to initialize Groups#List");
            throw ConnectorException.wrap(e);
        }
    }

    protected ConnectorObject fromRole(Role role, GCPPolicyObject p, Set<String> attributesToGet) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(ObjectClass.GROUP);

        if (null != role.getEtag()) {
            builder.setUid(new Uid(role.getName(), role.getEtag().toString()));
        } else {
            builder.setUid(role.getName());
        }
        builder.setName(role.getName());
        builder.addAttribute(AttributeBuilder.build(PROJECT_ATTR, this.project));

        if (null == attributesToGet || attributesToGet.contains(DESCRIPTION_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(DESCRIPTION_ATTR, role.getDescription()));
        }

        if (null == attributesToGet || attributesToGet.contains(TITLE_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(TITLE_ATTR, role.getTitle()));
        }

        if (null == attributesToGet || attributesToGet.contains(INCLUDED_PERMISSIONS_COUNT_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(INCLUDED_PERMISSIONS_COUNT_ATTR, role.getIncludedPermissionsCount() + ""));
        }

        RoleBinding rb = p.getBinding(role.getName());
        if (rb != null) {
            // Add attributes with the lists of member names
            if (!rb.getGroupMembers().isEmpty()) {
                builder.addAttribute(AttributeBuilder.build(GROUP_MEMBERS_ATTR, rb.getGroupMembers()));
            }
            if (!rb.getUserMembers().isEmpty()) {
                builder.addAttribute(AttributeBuilder.build(USER_MEMBERS_ATTR, rb.getUserMembers()));
            }
            if (!rb.getServiceAccountMembers().isEmpty()) {
                builder.addAttribute(AttributeBuilder.build(SERVICE_ACCOUNT_MEMBERS_ATTR, rb.getServiceAccountMembers()));
            }
        }

        return builder.build();
    }

    @Override
    public Void visitAndFilter(ListRolesRequest.Builder builder, AndFilter filter) {
        logger.warn("not-implemented : visitAndFilter");
        return null;
    }

    @Override
    public Void visitContainsFilter(ListRolesRequest.Builder builder, ContainsFilter filter) {
        logger.warn("not-implemented : visitContainsFilter");
        return null;
    }

    @Override
    public Void visitContainsAllValuesFilter(ListRolesRequest.Builder builder, ContainsAllValuesFilter filter) {
        logger.warn("not-implemented : visitContainsAllValuesFilter");
        return null;
    }

    @Override
    public Void visitEqualsFilter(ListRolesRequest.Builder builder, EqualsFilter filter) {
        logger.warn("not-implemented : visitEqualsFilter");
        return null;
    }

    @Override
    public Void visitExtendedFilter(ListRolesRequest.Builder builder, Filter filter) {
        logger.warn("not-implemented : visitExtendedFilter");
        return null;
    }

    @Override
    public Void visitGreaterThanFilter(ListRolesRequest.Builder builder, GreaterThanFilter filter) {
        logger.warn("not-implemented : visitGreaterThanFilter");
        return null;
    }

    @Override
    public Void visitGreaterThanOrEqualFilter(ListRolesRequest.Builder builder, GreaterThanOrEqualFilter filter) {
        logger.warn("not-implemented : visitGreaterThanOrEqualFilter");
        return null;
    }

    @Override
    public Void visitLessThanFilter(ListRolesRequest.Builder builder, LessThanFilter filter) {
        logger.warn("not-implemented : visitLessThanFilter");
        return null;
    }

    @Override
    public Void visitLessThanOrEqualFilter(ListRolesRequest.Builder builder, LessThanOrEqualFilter filter) {
        logger.warn("not-implemented : visitLessThanOrEqualFilter");
        return null;
    }

    @Override
    public Void visitNotFilter(ListRolesRequest.Builder builder, NotFilter filter) {
        logger.warn("not-implemented : visitNotFilter");
        return null;
    }

    @Override
    public Void visitOrFilter(ListRolesRequest.Builder builder, OrFilter filter) {
        logger.warn("not-implemented : visitOrFilter");
        return null;
    }

    @Override
    public Void visitStartsWithFilter(ListRolesRequest.Builder builder, StartsWithFilter filter) {
        logger.warn("not-implemented : visitStartsWithFilter");
        return null;
    }

    @Override
    public Void visitEndsWithFilter(ListRolesRequest.Builder builder, EndsWithFilter filter) {
        logger.warn("not-implemented : visitEndsWithFilter");
        return null;
    }

    @Override
    public Void visitEqualsIgnoreCaseFilter(ListRolesRequest.Builder builder, EqualsIgnoreCaseFilter filter) {
        logger.warn("not-implemented : visitEqualsIgnoreCaseFilter");
        return null;
    }
}
