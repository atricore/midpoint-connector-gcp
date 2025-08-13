package com.atricore.iam.midpoint.connector.gcp.handlers;

import com.atricore.iam.midpoint.connector.gcp.cache.ConnectorObjectsCache;
import com.atricore.iam.midpoint.connector.gcp.cache.GCPPolicyObject;
import com.google.cloud.iam.admin.v1.IAMClient;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.Set;

public class PrincipalHandler<T extends Enum<PrincipalType>> {

    private static final Log logger = Log.getLog(PrincipalHandler.class);

    private final T type;
    private ConnectorObjectsCache objectsCache;
    private IAMClient client;

    public PrincipalHandler(T type, ConnectorObjectsCache objectsCache, IAMClient client) {
        this.type = type;
        this.objectsCache = objectsCache;
        this.client = client;
    }

    public T getType() {
        return type;
    }

    public static ObjectClassInfo getClassInfo(PrincipalType type) {
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(type.getName());

        builder.addAttributeInfo(Name.INFO);

        /*
        builder.addAttributeInfo(AttributeInfoBuilder.define(ROLES_ATTR).setMultiValued(true)
                .setReturnedByDefault(true).build());
        */

        AttributeInfo GROUPS = AttributeInfoBuilder.build( PredefinedAttributes.GROUPS_NAME, String.class, java.util.EnumSet.of(AttributeInfo.Flags.MULTIVALUED));
        builder.addAttributeInfo(GROUPS);

        return builder.build();
    }

    /**
     * Name and UID are the same value!
     * @param p
     * @param name
     * @param handler
     * @param options
     * @param attributesToGet
     */
    public void executeReadQuery(GCPPolicyObject p, Name name, final ResultsHandler handler, OperationOptions options, final Set<String> attributesToGet) {
        try {
            // Try the cache first
            ConnectorObject cachedPrincipal = objectsCache.getPrincipal(name.getNameValue());
            if (cachedPrincipal != null) {
                handler.handle(cachedPrincipal);
                return;
            }

            logger.info("Looking for principal: ["+name.getNameValue()+"] ");

            ConnectorObject o = null;
            if (getType().equals(PrincipalType.GWS_ACCOUNT)) {
                o = fromAttrs(name.getNameValue(), p.getRolesByUser(name.getNameValue()));

            } else if (getType().equals(PrincipalType.GWS_GROUP)) {
                o = fromAttrs(name.getNameValue(), p.getRolesByGroup(name.getNameValue()));

            } else if (getType().equals(PrincipalType.GCP_SVC_ACCOUNT)) {
                o = fromAttrs(name.getNameValue(), p.getRolesByServiceAccount(name.getNameValue()));

            } else {
                throw new ConnectorException("Invalid object type: " + type);
            }
            objectsCache.addRole(o);
            handler.handle(o);

        } catch (Exception e) {
            logger.warn(e, "Failed to initialize Groups#Get");
            throw ConnectorException.wrap(e);
        }
    }

    public void executeReadQuery(GCPPolicyObject p, Uid uid, final ResultsHandler handler, OperationOptions options, final Set<String> attributesToGet) {
        try {
            // Try the cache first
            ConnectorObject cachedPrincipal = objectsCache.getPrincipal(uid.getUidValue());
            if (cachedPrincipal != null) {
                handler.handle(cachedPrincipal);
                return;
            }

            logger.info("Looking for principal: ["+uid.getUidValue()+"] ");

            ConnectorObject o = null;
            if (getType().equals(PrincipalType.GWS_ACCOUNT)) {
                o = fromAttrs(uid.getUidValue(), p.getRolesByUser(uid.getUidValue()));

            } else if (getType().equals(PrincipalType.GWS_GROUP)) {
                o = fromAttrs(uid.getUidValue(), p.getRolesByGroup(uid.getUidValue()));

            } else if (getType().equals(PrincipalType.GCP_SVC_ACCOUNT)) {
                o = fromAttrs(uid.getUidValue(), p.getRolesByServiceAccount(uid.getUidValue()));

            } else {
                throw new ConnectorException("Invalid object type: " + type);
            }
            objectsCache.addRole(o);
            handler.handle(o);

        } catch (Exception e) {
            logger.warn(e, "Failed to initialize Groups#Get");
            throw ConnectorException.wrap(e);
        }
    }

    public void executeSearchQuery(GCPPolicyObject p,
                                   Filter query,
                                   final ResultsHandler handler,
                                   OperationOptions options, final Set<String> attributesToGet) {

        // TODO : add support for query filters
        if (getType().equals(PrincipalType.GWS_ACCOUNT)) {
            p.getAllUsers().stream().map(u ->fromAttrs(u, p.getRolesByUser(u))).forEach(handler::handle);
        } else if (getType().equals(PrincipalType.GWS_GROUP)) {
            p.getAllGroups().stream().map(u ->fromAttrs(u, p.getRolesByGroup(u))).forEach(handler::handle);
        } else if (getType().equals(PrincipalType.GCP_SVC_ACCOUNT)) {
            p.getAllServiceAccounts().stream().map(u ->fromAttrs(u, p.getRolesByServiceAccount(u))).forEach(handler::handle);
        } else {
            throw new ConnectorException("Search for object type: " + type + " is not supported!");
        }
    }

    protected ConnectorObject fromAttrs(String uid, Set<String> roles) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();

        logger.info("Building principal for uid: ["+uid+"]");

        builder.setObjectClass(ObjectClass.ACCOUNT);
        builder.setName(uid);
        builder.setUid(uid);

        if (roles != null && !roles.isEmpty())
            builder.addAttribute(AttributeBuilder.build(PredefinedAttributes.GROUPS_NAME, roles));

        ConnectorObject o = builder.build();
        this.objectsCache.addPrincipal(o);
        return o;
    }

}
