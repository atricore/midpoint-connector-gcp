package com.atricore.iam.midpoint.connector.gcp.cache;

import com.google.iam.v1.Policy;
import com.google.protobuf.ByteString;
import org.identityconnectors.common.logging.Log;

import java.util.*;

import static com.atricore.iam.midpoint.connector.gcp.GoogleCloudConnector.getRoleName;

public class GCPPolicyObject {

    private static final Log logger = Log.getLog(GCPPolicyObject.class);

    //private Policy p;
    private Map<String, RoleBinding> roleBindings;
    private ByteString eTag;

    private Map<String, Set<String>> users;
    private Map<String, Set<String>> groups;
    private Map<String, Set<String>> serviceAccounts;
    private Policy policy;

    public GCPPolicyObject(Policy p) {
        this.roleBindings = new HashMap<>();
        this.policy = p;
        load(p);
    }

    public ByteString getETag() {
        return eTag;
    }

    public Policy getPolicy() {
        return policy;
    }

    protected void load(Policy p) {
        this.eTag = p.getEtag();
        this.users = new HashMap<>();
        this.groups = new HashMap<>();
        this.serviceAccounts = new HashMap<>();

        logger.info("Loading Policy ... ");
        p.getBindingsList()
                .stream()
                .map(b -> {
                    List<String> groupMembers = new java.util.ArrayList<>();
                    List<String> userMembers = new java.util.ArrayList<>();
                    List<String> serviceAccountMembers = new java.util.ArrayList<>();

                    b.getMembersList().forEach(m -> {
                        logger.info("Processing member: [" + m + "] for group [" + b.getRole() + "]");
                        if (m.startsWith("group:")) {
                            // Remove 'group:' prefix from m
                            String name = m.substring("group:".length());
                            register(this.groups, name, getRoleName(b.getRole()));
                            groupMembers.add(name);
                        } else if (m.startsWith("user:")) {
                            // Remove 'user:' prefix from m
                            String name = m.substring("user:".length());
                            register(this.users, name, getRoleName(b.getRole()));
                            userMembers.add(name);
                        } else if (m.startsWith("serviceAccount:")) {
                            // Remove 'serviceAccount:' prefix from m
                            String name = m.substring("serviceAccount:".length());
                            register(this.serviceAccounts, name, getRoleName(b.getRole()));
                            serviceAccountMembers.add(name);
                        }
                    });

                    return new RoleBinding(b.getRole(), groupMembers, userMembers, serviceAccountMembers);

                }).forEach(rb -> this.roleBindings.put(rb.getName(), rb));
    }

    protected void register(Map<String, Set<String>> mappings, String name, String role) {
        logger.info("Registering role ["+role+"] for principal [" + name + "]");
        // Get list of roles for principal
        Set<String> roles = mappings.get(name);
        if (roles == null) {
            // Initialize list of roles if needed
            roles = new HashSet<>();
            mappings.put(name, roles);
        }
        // Add role to principal
        roles.add(role);
    }

    public RoleBinding getBinding(String roleName) {
        return this.roleBindings.get(roleName);
    }

    public Set<String> getAllUsers() {
        return this.users.keySet();
    }

    public Set<String> getAllGroups() {
        return this.groups.keySet();
    }

    public Set<String> getAllServiceAccounts() {
        return this.serviceAccounts.keySet();
    }

    public Set<String> getRolesByUser(String name) {
        return this.users.get(name);
    }
    public Set<String> getRolesByGroup(String name) {
        return this.groups.get(name);
    }
    public Set<String> getRolesByServiceAccount(String name) {
        return this.serviceAccounts.get(name);
    }

    public List<String> getUsers(String roleName) {
        return Optional.ofNullable(this.roleBindings.get(roleName))
                .map(RoleBinding::getUserMembers)
                .orElse(Collections.emptyList());
    }

    public List<String> getGroups(String roleName) {
        return Optional.ofNullable(this.roleBindings.get(roleName))
                .map(RoleBinding::getGroupMembers)
                .orElse(Collections.emptyList());
    }

    public List<String> getServiceAccounts(String roleName) {
        return Optional.ofNullable(this.roleBindings.get(roleName))
                .map(RoleBinding::getServiceAccountMembers)
                .orElse(Collections.emptyList());
    }
}
