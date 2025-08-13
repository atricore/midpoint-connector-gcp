package com.atricore.iam.midpoint.connector.gcp;

import com.atricore.iam.midpoint.connector.gcp.cache.ConnectorObjectsCache;
import com.atricore.iam.midpoint.connector.gcp.cache.GCPPolicyObject;
import com.atricore.iam.midpoint.connector.gcp.handlers.RoleHandler;
import com.atricore.iam.midpoint.connector.gcp.handlers.PrincipalHandler;
import com.atricore.iam.midpoint.connector.gcp.handlers.PrincipalType;
import com.evolveum.polygon.common.GuardedStringAccessor;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.iam.admin.v1.IAMSettings;
import com.google.cloud.resourcemanager.v3.ProjectsClient;
import com.google.cloud.resourcemanager.v3.ProjectsSettings;
import com.google.iam.admin.v1.*;
import com.google.iam.v1.Binding;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.Policy;
import com.google.cloud.iam.admin.v1.IAMClient;
import com.google.iam.v1.SetIamPolicyRequest;
import com.google.protobuf.FieldMask;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.*;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.identityconnectors.framework.common.objects.Name.NAME;

/**
 * Required GCP permissions for the Service Account:
 *
 * At the ORG LEVEL:
 * roles/iam.organizationRoleViewer (if custom roles at the org level are used)
 *
 * At the PROJECT LEVEL:
 * roles/iam.roleViewer
 * roles/resourcemanager.projectIamAdmin (Project IAM Admin)
 *
 * Google GCP Policy object example:
 *
 *    {
 *       "bindings": [
 *         {
 *           "role": "roles/resourcemanager.organizationAdmin",
 *           "members": [
 *             "user:mike@example.com",
 *             "group:admins@example.com",
 *             "domain:google.com",
 *             "serviceAccount:my-project-id@appspot.gserviceaccount.com"
 *           ]
 *         },
 *         {
 *           "role": "roles/resourcemanager.organizationViewer",
 *           "members": [
 *             "user:eve@example.com"
 *           ],
 *           "condition": {
 *             "title": "expirable access",
 *             "description": "Does not grant access after Sep 2020",
 *             "expression": "request.time < timestamp('2020-10-01T00:00:00.000Z')",
 *           }
 *         }
 *       ],
 *       "etag": "BwWWja0YfJA=",
 *       "version": 3
 *     }
 */
@ConnectorClass(displayNameKey = "GoogleCloud.connector.display",
        configurationClass = GoogleCloudConfiguration.class)
public class GoogleCloudConnector implements
        Connector
        , SchemaOp
        , CreateOp
        //, DeleteOp
        , SearchOp<Filter>
        , TestOp
        , UpdateOp {

    public static final ObjectClass GCP_ROLE = new ObjectClass("GCPRole");
    public static final ObjectClass GWS_ACCOUNT = PrincipalType.GWS_ACCOUNT.getObjectClass();
    public static final ObjectClass GWS_GROUP = PrincipalType.GWS_GROUP.getObjectClass();
    public static final ObjectClass GCP_SVC_ACCOUNT = PrincipalType.GCP_SVC_ACCOUNT.getObjectClass();

    public static final String NAME_ETAG = "name,etag";
    public static final String NAME_ATTR = "name";
    public static final String ETAG_ATTR = "etag";

    public static final String INCLUDED_PERMISSIONS_COUNT_ATTR = "included_permissions_count";
    public static final String INCLUDED_PERMISSIONS_LIST_ATTR = "included_permissions_list";

    public static final String TITLE_ATTR = "title";
    public static final String PROJECT_ATTR = "project";
    public static final String DESCRIPTION_ATTR = "description";

    public static final String ROLES_ATTR = "ROLES";
    public static final String USER_MEMBERS_ATTR = "USERS";
    public static final String GROUP_MEMBERS_ATTR = "GROUPS";
    public static final String SERVICE_ACCOUNT_MEMBERS_ATTR = "SERVICE_ACCOUNTS";

    private static final Log logger = Log.getLog(GoogleCloudConnector.class);

    private GoogleCloudConfiguration configuration;
    private ConnectorObjectsCache objectsCache;
    private Schema schema = null;

    // client
    private IAMClient client;
    private ProjectsClient projectsClient;

    private Throwable initError;

    // --------------------------------------------------------------------------------
    // Connector

    @Override
    public Configuration getConfiguration() {
        return this.configuration;
    }

    @Override
    public void init(Configuration cfg) {
        this.configuration = (GoogleCloudConfiguration) cfg;
        this.objectsCache = ConnectorObjectsCache.getInstance(this.configuration, logger);

        try {

            GuardedStringAccessor a = new GuardedStringAccessor();
            this.configuration.getServiceAccountKeyJson().access(a);
            ByteArrayInputStream credentialsStream = new ByteArrayInputStream(a.getClearString().getBytes());
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(credentialsStream);

            logger.info("creating IAMClient .... ");
            IAMClient c = IAMClient.create(IAMSettings.newBuilder().setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build());

            // Create the ProjectsClient with these settings
            logger.info("creating ProjectsClient .... ");
            ProjectsClient p = ProjectsClient.create(ProjectsSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build());

            // Store clients
            this.projectsClient = p;
            this.client = c;

        } catch (IOException e) {
            logger.error(e, "Can't create Projects client: " + e.getMessage());
            throw ConnectorException.wrap(e);
        }

    }

    @Override
    public void dispose() {
        this.configuration = null;

        if (this.client != null)
            this.client.shutdown();

        this.client = null;

        if (this.projectsClient != null)
            this.projectsClient.shutdown();

        this.projectsClient = null;
    }

    // --------------------------------------------------------------------------------
    // Schema Op
    @Override
    public Schema schema() {
        if (null == schema) {
            final SchemaBuilder builder = new SchemaBuilder(GoogleCloudConnector.class);

            // Users (gws)
            builder.defineObjectClass(PrincipalHandler.getClassInfo(PrincipalType.GWS_ACCOUNT));

            // Groups (gws)
            builder.defineObjectClass(PrincipalHandler.getClassInfo(PrincipalType.GWS_GROUP));

            // Service account (gcp)
            builder.defineObjectClass(PrincipalHandler.getClassInfo(PrincipalType.GCP_SVC_ACCOUNT));

            // Role GCP
            ObjectClassInfo role = RoleHandler.getClassInfo();
            builder.defineObjectClass(role);
            schema = builder.build();
        }
        return schema;
    }

    // --------------------------------------------------------------------------------
    // Test Op
    @Override
    public void test() {
        logger.info("Testing connection... ");
        try {
            ListRolesRequest r = ListRolesRequest.newBuilder().setPageSize(10).build();
            IAMClient.ListRolesPagedResponse s = this.client.listRoles(r);
        } catch (Exception e) {
            logger.error("failed: {0}", e);
            throw ConnectorException.wrap(e);
        }
        logger.info("OK.");
    }

    // --------------------------------------------------------------------------------
    // SearchOp

    @Override
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        return new FilterTranslator<Filter>() {
            public List<Filter> translate(Filter filter) {
                return CollectionUtil.newList(filter);
            }
        };
    }

    @Override
    public void executeQuery(ObjectClass objectClass, Filter query, final ResultsHandler handler,
                             OperationOptions options) {
        final long startTime = System.currentTimeMillis();
        final Set<String> attributesToGet = getAttributesToGet(objectClass, options);
        Uid uid = null;
        Name name = null;
        if (query instanceof EqualsFilter && ((EqualsFilter) query).getAttribute() instanceof Uid) {
            // Read request
            uid = (Uid) ((EqualsFilter) query).getAttribute();
            logger.info("executeQuery() - objectClass: " + objectClass +
                    ", uid: " + uid.getUidValue());
        } else if (query instanceof EqualsFilter && ((EqualsFilter) query).getAttribute() instanceof Name) {
            name = (Name) ((EqualsFilter) query).getAttribute();
            logger.warn("executeQuery() - objectClass: " + objectClass +
                    ", name: " + name.getNameValue());
        } else if (query != null) {
            logger.warn("executeQuery() - objectClass: " + objectClass +
                    ", query: " + query.getClass());
        }

        if (GWS_ACCOUNT.equals(objectClass)) {
            PrincipalHandler<PrincipalType> ph = new PrincipalHandler<>(PrincipalType.GWS_ACCOUNT, this.objectsCache, this.client);
            GCPPolicyObject p = getGCPPolicy();
            if (null != uid) {
                // Search request
                ph.executeReadQuery(p, uid, handler, options, attributesToGet);
            } else if (name != null) {
                ph.executeReadQuery(p, name, handler, options, attributesToGet);
            } else {
                ph.executeSearchQuery(p, query, handler, options, attributesToGet);
            }
        } else if (GWS_GROUP.equals(objectClass)) {
            PrincipalHandler<PrincipalType> ph = new PrincipalHandler<>(PrincipalType.GWS_GROUP, this.objectsCache, this.client);
            GCPPolicyObject p = getGCPPolicy();
            if (null == uid) {
                // Search request
                ph.executeSearchQuery(p, query, handler, options, attributesToGet);

            } else {
                // Read request
                ph.executeReadQuery(p, uid, handler, options, attributesToGet);

            }
        } else if (GCP_SVC_ACCOUNT.equals(objectClass)) {
            PrincipalHandler<PrincipalType> ph = new PrincipalHandler<>(PrincipalType.GCP_SVC_ACCOUNT, this.objectsCache, this.client);
            GCPPolicyObject p = getGCPPolicy();
            if (null == uid) {
                // Search request
                ph.executeSearchQuery(p, query, handler, options, attributesToGet);

            } else {
                // Read request
                ph.executeReadQuery(p, uid, handler, options, attributesToGet);

            }
        } else if (GCP_ROLE.equals(objectClass)) {

            RoleHandler rh = new RoleHandler(this.configuration.getOrgId(),
                    this.configuration.getProject(),
                    this.objectsCache,
                    this.client);

            GCPPolicyObject p = getGCPPolicy();

            if (null != uid) {
                // Read request
                rh.executeRoleReadQuery(p, uid, handler, options, attributesToGet);
            } else if (null != name) {
                rh.executeRoleReadQuery(p, name, handler, options, attributesToGet);

            } else {
                // Search request
                rh.executeRoleSearchQuery(p, query, handler, options, attributesToGet);
            }
        } else {
            logger.warn("Search of type {0} is not supported", configuration.getConnectorMessages()
                    .format(objectClass.getDisplayNameKey(), objectClass.getObjectClassValue()));
            throw new UnsupportedOperationException("Search of type "
                    + objectClass.getObjectClassValue() + " is not supported");
        }

        logger.info("executeQuery() - finished in " + timeFrom(startTime));
    }

    protected Set<String> getAttributesToGet(ObjectClass objectClass, OperationOptions options) {
        Set<String> attributesToGet = null;
        if (null != options.getAttributesToGet()) {
            attributesToGet = CollectionUtil.newCaseInsensitiveSet();
            attributesToGet.add(NAME_ATTR);
            attributesToGet.add(ETAG_ATTR);
            for (String attribute : options.getAttributesToGet()) {
                int i = attribute.indexOf('/');
                if (i == 0) {
                    // Strip off the leading '/'
                    attribute = attribute.substring(1);
                    i = attribute.indexOf('/');
                }
                int j = attribute.indexOf('(');
                if (i < 0 && j < 0) {
                    attributesToGet.add(attribute);
                } else if (i == 0 || j == 0) {
                    throw new IllegalArgumentException("Invalid attribute name to get:/"
                            + attribute);
                } else {
                    int l = attribute.length();
                    if (i > 0) {
                        l = Math.min(l, i);
                    }
                    if (j > 0) {
                        l = Math.min(l, j);
                    }
                    attributesToGet.add(attribute.substring(0, l));
                }
            }
        }
        return attributesToGet;
    }


    // ----------------------------------------------------------------------
    // udpateOp

    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes, OperationOptions options) {

        if (GCP_ROLE.equals(objectClass)) {

            // Update binding ?!
            String name = uid.getUidValue();

            // Local representation of a Policy
            GCPPolicyObject gcpPolicy = getGCPPolicy();
            Policy updatedPolicy = gcpPolicy.getPolicy();
            // serialize and backup before update
            backupPolicy(updatedPolicy);

            for (Attribute a : replaceAttributes) {
                String attributeName = a.getName();
                List<Object> attributeValues = a.getValue();

                switch (attributeName) {
                    case USER_MEMBERS_ATTR:
                        logger.info("Updating users for role ["+name+"]");
                        updatedPolicy = updateMembers(updatedPolicy, name, PrincipalType.GWS_ACCOUNT.getPolicyType(), attributeValues);
                        break;
                    case GROUP_MEMBERS_ATTR:
                        logger.info("Updating groups for role ["+name+"]");
                        updatedPolicy = updateMembers(updatedPolicy, name, PrincipalType.GWS_GROUP.getPolicyType(), attributeValues);
                        break;
                    // Add more cases as needed
                    default:
                        // Handle unexpected attribute types if necessary
                        throw new UnsupportedOperationException("Attribute cannot be updated: " +  attributeName);
                }
            }

            // We have an updated policy
            try {
                updatedPolicy = setProjectPolicy(updatedPolicy);
                gcpPolicy = new GCPPolicyObject(updatedPolicy);
                this.objectsCache.setPolicy(gcpPolicy);
                return uid;
            } catch (Exception e) {
                logger.error("Error updating policy during update [ " + objectClass.getObjectClassValue() + "->" + uid.getUidValue() + "] : " + e.getMessage(), e);
                throw new ConnectorException(e);
            }

        } else {
            logger.error("ObjectClass: " + objectClass.getDisplayNameKey() + " cannot be updated");
            throw new UnsupportedOperationException("ObjectClass: " + objectClass.getObjectClassValue() + " cannot be updated");
        }

    }

    // ----------------------------------------------------------------------
    // createOp

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> createAttributes, OperationOptions options) {

        // We can't create any accounts: GWS Account or GWS Group, because they only exists in the Policy
        // object as member of a binding. We simply return a UID based on the name.
        Optional<Attribute> name = createAttributes.stream().filter(a -> a.getName().equals(NAME)).findFirst();
        Optional<PrincipalType> type = PrincipalType.fromClass(objectClass);

        if (name.isPresent() && type.isPresent()) {
            String strName = (String) name.get().getValue().get(0);
            Uid uidName = new Uid(strName);
            GCPPolicyObject policy = getGCPPolicy();

            if (PrincipalType.GWS_ACCOUNT.getObjectClass().equals(objectClass)) {
                if (policy.getAllUsers().contains(strName)) {
                    throw new AlreadyExistsException(strName +
                            " for " + objectClass.getObjectClassValue() +
                            " at project " + this.configuration.getProject());
                }
            } else if (PrincipalType.GWS_GROUP.getObjectClass().equals(objectClass)) {
                if (policy.getAllGroups().contains(strName)) {
                    throw new AlreadyExistsException(strName +
                            " for " + objectClass.getObjectClassValue() +
                            " at project " + this.configuration.getProject());
                }
            }
            return uidName;
        }

        if (GCP_ROLE.equals(objectClass)) {
            logger.error("Creating GCP role not allowed!");
            // TODO : in the future , add support for creating GCP roles
            throw new ConnectorException("ObjectClass:" + objectClass.getObjectClassValue() + " cannot be created!");
        }

        throw new ConnectorException("ObjectClass:" + objectClass.getObjectClassValue() + " cannot be created!");
    }

    // --------------------------------------------------------------------------------
    // DeleteOp

/*
    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        // A delete means that the account should not be in the Policy object any more!
        String t = "";
        if (GWS_ACCOUNT.equals(objectClass)) {
            t = PrincipalType.GWS_ACCOUNT.getPolicyType();
        } else if (GWS_GROUP.equals(objectClass)) {
            t = PrincipalType.GWS_GROUP.getPolicyType();
        } else {
            throw new ConnectorException("ObjectClass:" + objectClass.getObjectClassValue() + " cannot be deleted!");
        }

        GCPPolicyObject gcpPolicy = getGCPPolicy();
        final String type = t;
        // Clone bindings from policy
        Policy lastPolicy = gcpPolicy.getPolicy();
        backupPolicy(lastPolicy);

        List<Binding> bindings = new ArrayList<>(gcpPolicy.getPolicy().getBindingsList());

        // Go over each binding, and remove the account (user, group, etc)
        logger.info("Removing principal from policy: " + type + ":" + uid.getUidValue());
        for (Binding b : bindings) {
            // Remove the principal from the list of members
            List<String> replaceMembers = b.getMembersList().stream().filter(m -> !m.equals(type + ":" + uid.getUidValue())).collect(Collectors.toList());
            lastPolicy = setMembers(lastPolicy, b.getRole(), type, replaceMembers);
        }

        // We have an updated policy
        try {
            Policy updatedPolicy = setProjectPolicy(lastPolicy);
            gcpPolicy = new GCPPolicyObject(updatedPolicy);
            this.objectsCache.setPolicy(gcpPolicy);
        } catch (Exception e) {
            logger.error("Error updating policy during delete [ " + objectClass.getObjectClassValue() + "/" + uid.getUidValue() + "] : " + e.getMessage(), e);
            throw new ConnectorException(e);
        }

    }

 */

    protected Policy updateMembers(Policy p, String role, String type, List<Object> principals) {
        // We need to build a new members list, with existing members, but replacing those that are of the provided type.
        List<String> replaceMembers = principals.stream()
                .filter(o -> o instanceof String)
                .map(o -> type + ":" + o)
                .collect(Collectors.toList());

        if (!hasBinding(p, role)) {
            if (!principals.isEmpty()) {
                logger.info("Adding new binding for role [" + role + "] of type [" + type + "]");
                p = addBinding(p, role, replaceMembers);
            }
        } else {
            if (!principals.isEmpty()) {
                logger.info("Adding new member to role [" + role + "] of type [" + type + "]");
                p = setMembers(p, role, type, replaceMembers);
            } else {
                logger.info("Deleting all members to role [" + role + "] of type [" + type + "]");
                return deleteBinding(p, role);
            }
        }

        return p;

    }

    protected boolean hasBinding(Policy policy, String role) {
        Optional<String> binding = policy
                .getBindingsList()
                .stream()
                .map(b -> getRoleName(b.getRole()))
                .filter(role::equals).findFirst();

        return binding.isPresent();
    }

    protected Policy addBinding(Policy policy, String role, List<String> members) {
        Binding binding = Binding.newBuilder()
                .setRole(role)
                .addAllMembers(members)
                .build();

        // Update bindings for the policy.
        Policy updatedPolicy = policy.toBuilder().addBindings(binding).build();
        logger.info("Added binding: " + updatedPolicy.getBindingsList());
        return updatedPolicy;
    }

    protected Policy deleteBinding(Policy policy, String role) {
        int idx = -1;
        int indexToRemove = -1;
        for (Binding b : policy.getBindingsList()) {
            idx ++;
            if (b.getRole().equals(role)) {
                indexToRemove = idx;
                break;
            }
        }

        if (indexToRemove >= 0) {
            // Update bindings for the policy.
            logger.info("Deleted binding: " + indexToRemove);
            return policy.toBuilder().removeBindings(1).build();

        }

        return policy;
    }

    // Adds a member to a pre-existing role.
    protected Policy setMembers(Policy policy, String role, String type, List<String> members) {
        List<Binding> newBindingsList = new ArrayList<>();

        for (Binding b : policy.getBindingsList()) {
            String rName = getRoleName(b.getRole());
            if (rName.equals(role)) {
                Binding.Builder builder = b.toBuilder();

                // Keep all members of other types
                List<String> replaceMembers = b.getMembersList().stream().filter(m -> !m.startsWith(type)).collect(Collectors.toList());

                // Only add the provided list of members for our type
                replaceMembers.addAll(members);

                // Rebuild binding
                builder.clearMembers();
                builder.addAllMembers(replaceMembers);
                newBindingsList.add(builder.build());

            } else {
                // For other bindings, keep them as they are
                newBindingsList.add(b);
            }
        }

        // Update the policy to add the member.
        Policy updatedPolicy = policy.toBuilder()
                .clearBindings()
                .addAllBindings(newBindingsList)
                .build();

        logger.info("Added member: " + updatedPolicy.getBindingsList());

        return updatedPolicy;
    }

    protected Policy setProjectPolicy(Policy policy) {

        String projectId = this.configuration.getProject();

        List<String> paths = Arrays.asList("bindings", "etag");
        SetIamPolicyRequest request = SetIamPolicyRequest.newBuilder()
                .setResource(ProjectName.of(projectId).toString())
                .setPolicy(policy)
                // A FieldMask specifying which fields of the policy to modify. Only
                // the fields in the mask will be modified. If no mask is provided, the
                // following default mask is used:
                // `paths: "bindings, etag"`
                .setUpdateMask(FieldMask.newBuilder().addAllPaths(paths).build())
                .build();

        return this.projectsClient.setIamPolicy(request);

    }

    protected void backupPolicy(Policy policy) {

        if (!this.configuration.isBackupPolicy()) {
            logger.error("Policy backup disabled ... ");
            return;
        }

        String backupDirPath = this.configuration.getBackupPolicyLocation();
        File backupDir = new File(backupDirPath);

        // Ensure the backup directory exists
        if (!backupDir.exists()) {
            boolean dirsCreated = backupDir.mkdirs();
            if (!dirsCreated) {
                logger.error("Failed to create backup directory: " + backupDir.getAbsolutePath());
                return;
            }
        }

        // Sanitize the project name to remove invalid filename characters
        String projectName = this.configuration.getProject();
        String sanitizedProjectName = projectName.replaceAll("[\\\\/:*?\"<>|]", "_");

        // Create a timestamp for the backup file name
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String backupFileName = sanitizedProjectName + "-gcp-policy-" + timestamp + ".ser";
        File backupFile = new File(backupDir, backupFileName);

        // Serialize the Policy object to the backup file
        try (FileOutputStream fileOut = new FileOutputStream(backupFile);
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {

            out.writeObject(policy);
            out.flush();
            logger.info("Policy has been backed up to " + backupFile.getAbsolutePath());

        } catch (IOException e) {
            logger.error("Failed to back up policy: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------------

    protected GCPPolicyObject getGCPPolicy() {
        String resource = this.configuration.getProject();
        logger.info("Getting policy for [" + resource + "]");
        GCPPolicyObject pb = this.objectsCache.getPolicy();
        if (pb == null) {
            logger.info("Loading policy for [" + resource + "]");
            GetIamPolicyRequest request = GetIamPolicyRequest.newBuilder()
                    .setResource(ProjectName.of(resource).toString())
                    .build();
            Policy p = projectsClient.getIamPolicy(request);
            pb = new GCPPolicyObject(p);
            this.objectsCache.setPolicy(pb);
        }
        return pb;
    }

    // ----------------------------------------------------------------------
    private String timeFrom(long startTime) {
        return (System.currentTimeMillis() - startTime) + " ms";
    }

    /**
     * Sanitize GCP grole name (it adds _withcond_xxxxxx markers when conditions are used)
     * @param gcpRole
     * @return
     */
    public static String getRoleName(String gcpRole) {
        String marker = "_withcond_";
        String rName = gcpRole;
        if (rName.contains(marker)) {
            int index = rName.indexOf(marker);
            rName = rName.substring(0, index);
        }
        return rName;
    }
}
