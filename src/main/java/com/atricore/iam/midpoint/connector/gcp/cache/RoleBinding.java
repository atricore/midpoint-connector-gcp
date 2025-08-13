package com.atricore.iam.midpoint.connector.gcp.cache;

import java.util.List;

public class RoleBinding {

    private String name;
    List<String> groupMembers = new java.util.ArrayList<>();
    List<String> userMembers = new java.util.ArrayList<>();
    List<String> serviceAccountMembers = new java.util.ArrayList<>();

    public RoleBinding(String name, List<String> groupMembers, List<String> userMembers, List<String> serviceAccountMembers) {
        this.name = name;
        this.groupMembers = groupMembers;
        this.userMembers = userMembers;
        this.serviceAccountMembers = serviceAccountMembers;
    }

    public String getName() {
        return name;
    }

    public List<String> getGroupMembers() {
        return groupMembers;
    }

    public List<String> getUserMembers() {
        return userMembers;
    }

    public List<String> getServiceAccountMembers() {
        return serviceAccountMembers;
    }
}
