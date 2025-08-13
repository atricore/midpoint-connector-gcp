package com.atricore.iam.midpoint.connector.gcp.handlers;

import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Optional;

public enum PrincipalType {

    GWS_ACCOUNT("GWSAccount", "user"),
    GWS_GROUP("GWSGroup", "group"),
    GCP_SVC_ACCOUNT("GCPServiceAccount", "serviceAccount");

    private String name;
    private String policyType;
    private ObjectClass oClass;

    PrincipalType(String name, String policyType) {
        this.name = name;
        this.policyType = policyType;
        this.oClass = new ObjectClass(name);
    }

    public String getName() {
        return name;
    }

    public String getPolicyType() {
        return policyType;
    }

    public ObjectClass getObjectClass() {
        return oClass;
    }

    public static Optional<PrincipalType> fromClass(ObjectClass type) {
        for (PrincipalType principalType : values()) {
            if (principalType.name.equalsIgnoreCase(type.getObjectClassValue())) {
                return Optional.of(principalType);
            }
        }
        return Optional.empty();
    }

    public static Optional<PrincipalType> fromName(String type) {
        for (PrincipalType principalType : values()) {
            if (principalType.name.equalsIgnoreCase(type)) {
                return Optional.of(principalType);
            }
        }
        return Optional.empty();
    }
}
