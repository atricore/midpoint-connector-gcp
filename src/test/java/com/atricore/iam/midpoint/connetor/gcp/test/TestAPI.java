package com.atricore.iam.midpoint.connetor.gcp.test;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.cloud.iam.admin.v1.IAMClient;
import com.google.cloud.resourcemanager.v3.ProjectsClient;
import com.google.iam.admin.v1.*;
import com.google.iam.v1.*;
import com.google.protobuf.FieldMask;
import org.identityconnectors.common.StringUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TestAPI {

    private static final String IAM_PROJECT = "atricore-soc-443214";
    private static final String IAM_ORG = "71027239277";

    private static final String CLIENTSECRETS_LOCATION = "client_secrets.json";
    public static final java.lang.String FULL_ACCESS =
            "https://www.googleapis.com/auth/cloud-identity";
    public static final java.lang.String CLOUD_IDENTITY_USERS =
            "https://www.googleapis.com/auth/cloud-identity.user";
    public static final java.lang.String CLOUD_IDENTITY_GROUPS =
            "https://www.googleapis.com/auth/cloud-identity.groups";
    public static final java.lang.String CLOUD_IDENTITY_DEVICES =
            "https://www.googleapis.com/auth/cloud-identity.devices";
    public static final java.lang.String ADMIN_ENTERPRISE_LICENSE =
            "https://www.googleapis.com/auth/apps.licensing";
    // @formatter:off
    private static final List<String> SCOPES = Arrays.asList(
            FULL_ACCESS,
            CLOUD_IDENTITY_USERS,
            CLOUD_IDENTITY_GROUPS,
            CLOUD_IDENTITY_DEVICES,
            ADMIN_ENTERPRISE_LICENSE);
    // @formatter:on
    /**
     * Global instance of the HTTP transport.
     */
    /*
    private static final HttpTransport HTTP_TRANSPORT;

    static {
        HttpTransport t = null;
        try {
            t = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Exception e) {
            try {
                t = new NetHttpTransport.Builder().doNotValidateCertificate().build();
            } catch (GeneralSecurityException e1) {
            }
        }
        HTTP_TRANSPORT = t;
    } */
    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = new GsonFactory();

    private IAMClient client;
    private ProjectsClient projectsClient;

    public static void main(String[] args) throws Exception {
        System.out.println("-------------------------------------------------------------------------------------------");
        System.out.println("Testing GCP Connection!");
        System.out.println("-------------------------------------------------------------------------------------------");
        System.out.println("You have to created and registered App in Google API and Google API enabled.");
        System.out.println("Add these credentials into configuration fields in Google Apps Connector. See readme.txt.");
        System.out.println("-------------------------------------------------------------------------------------------");
        System.out.println("");

        TestAPI t = new TestAPI();
        t.init();
        //t.getStandardRoles();
        t.getProjectRoles(IAM_PROJECT);
        t.getOrgRoles(IAM_ORG);
        t.getPolicy("user:klehmann@atricore.com");
/*
        // Get Policy
        Policy p = getProjectPolicy(IAM_PROJECT);
        System.out.println("-------------------------------------------");
        System.out.println("Policy:" + p.getBindingsCount());

        p.getBindingsList().forEach(b -> {
            GetRoleRequest rq = GetRoleRequest.newBuilder().setName(b.getRole()).build();
            Role rp = this.client.getRole(rq);
            System.out.println("[" + b.getRole() + "]:" + rp.getTitle() + "/" + "/" + rp.getDescription() + " ->" + b.getMembersList().size());
            b.getMembersList().forEach(m -> System.out.println(m));
        });
*/
        /*

        // Add binding
        String role = "roles/firebasedataconnect.dataViewer";
        if (hasBinding(p, role)) {
            // Add member to policy
            p = addMember(p, role, "user:sgonzalez@atricore.com");
        } else {
            p = addBinding(p, role,  Collections.singletonList("user:sgonzalez@atricore.com"));
        }

        // Update policy object
        p = setProjectPolicy(p, IAM_PROJECT);

        // Updated policy?
        p = getProjectPolicy(IAM_PROJECT);
        */
    }

    public void getPolicy(String principal) throws Exception {
        Policy policy = getProjectPolicy(IAM_PROJECT);
        System.out.println("-------------------------------------------");
        System.out.println("Policy:" + policy.getBindingsCount());

        policy.getBindingsList().forEach(b -> {
            //b.getMembersList().forEach(m -> System.out.println(m));
            b.getMembersList().stream().filter(p -> p.equals(principal)).forEach(m -> {
                String rName = b.getRole();

                System.out.println("Condition:"+b.getCondition().getTitle());

                String marker = "_withcond_";
                if (rName.contains(marker)) {
                    int index = rName.indexOf(marker);
                    rName = rName.substring(0, index);
                }
                // If marker is not found, just return the original string

                System.out.println(b.getRole() + "/"+rName+"[" + m + "]");
                // Get role
                GetRoleRequest rq = GetRoleRequest.newBuilder().setName(rName).build();
                Role rp = this.client.getRole(rq);
                System.out.println("[" + m + "]:" + rp.getTitle() + "/" + "/" + rp.getDescription() + " ->" + b.getMembersList().size());
            //b.getMembersList().forEach(m -> System.out.println(m));
            });
        });
    }

    public boolean hasBinding(Policy policy, String role) {
        Optional<Binding> binding = policy.getBindingsList().stream().filter(b -> b.getRole().equals(role)).findFirst();
        return binding.isPresent();
    }

    public Policy addBinding(Policy policy, String role, List<String> members) {
        Binding binding = Binding.newBuilder()
                .setRole(role)
                .addAllMembers(members)
                .build();

        // Update bindings for the policy.
        Policy updatedPolicy = policy.toBuilder().addBindings(binding).build();
        System.out.println("Added binding: " + updatedPolicy.getBindingsList());
        return updatedPolicy;
    }

    // Adds a member to a pre-existing role.
    public Policy addMember(Policy policy, String role, String member) {
        List<Binding> newBindingsList = new ArrayList<>();
        for (Binding b : policy.getBindingsList()) {
            if (b.getRole().equals(role)) {
                newBindingsList.add(b.toBuilder().addMembers(member).build());
            } else {
                newBindingsList.add(b);
            }
        }

        // Update the policy to add the member.
        Policy updatedPolicy = policy.toBuilder()
                .clearBindings()
                .addAllBindings(newBindingsList)
                .build();

        System.out.println("Added member: " + updatedPolicy.getBindingsList());

        return updatedPolicy;
    }

    // Sets a project's policy.
    public Policy setProjectPolicy(Policy policy, String projectId)
            throws IOException {

        // Initialize client that will be used to send requests.
        // This client only needs to be created once, and can be reused for multiple requests.
        try (ProjectsClient projectsClient = ProjectsClient.create()) {
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

            return projectsClient.setIamPolicy(request);
        }
    }

    public void init() throws Exception {

        // Read the environment variable
        String envVar = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (envVar == null || envVar.isEmpty()) {
            throw new Exception("The environment variable GOOGLE_APPLICATION_CREDENTIALS is not set or is empty.");
        }

        // Check if the file exists
        File file = new File(envVar);
        if (file.exists() && file.isFile()) {
            System.out.println("The file pointed to by GOOGLE_APPLICATION_CREDENTIALS exists: " + envVar);
        } else {
            throw new Exception("The file pointed to by GOOGLE_APPLICATION_CREDENTIALS does not exist or is not a file: " + envVar);
        }

        this.client = IAMClient.create();
        this.projectsClient = ProjectsClient.create();
    }

    public void getProjectRoles(String project) {
        getRoles("projects/" + project );
    }

    public void getOrgRoles(String orgId) {
        getRoles("organizations/"  + orgId);
    }

    public void getStandardRoles() {
        getRoles("");
    }

    public void getRoles(String parent) {
        // Get all roles
        ListRolesRequest.Builder builder = ListRolesRequest.newBuilder().setParent(parent).setView(RoleView.FULL).setPageSize(500);
        String nextPageToken = null;
        int i = 1;
        do {
            if (StringUtil.isNotBlank(nextPageToken)) {
                builder.setPageToken(nextPageToken);
            }

            ListRolesRequest req = builder.build();
            IAMClient.ListRolesPagedResponse page = this.client.listRoles(req);

            for (Role rp : page.getPage().getValues()) {
                //handler.handle(fromRole(role, p, attributesToGet));
                //System.out.println("[" + rp.getName() + "]: #" + i+ " : " + rp.getTitle() + "/" + "/" + rp.getDescription());
                System.out.println("[" + rp.getName() + "]: #" + i);
                GetRoleRequest gReq = GetRoleRequest.newBuilder().setName(rp.getName()).build();
                Role r = this.client.getRole(gReq);
                System.out.println("[" + r.getName() + "]: #" + i);
                i++;
            }

            nextPageToken = page.getNextPageToken();
            //System.out.println("Next pageToken: " + nextPageToken);
            if (StringUtil.isNotBlank(nextPageToken))
                builder.setPageToken(nextPageToken);

        } while (StringUtil.isNotBlank(nextPageToken));
    }

    // Gets a project's policy.
    public Policy getProjectPolicy(String projectId) throws IOException {
        // Initialize client that will be used to send requests.
        // This client only needs to be created once, and can be reused for multiple requests.
        String resource = ProjectName.of(projectId).toString();
        System.out.println("resource: [" + resource + " ]");
        GetIamPolicyRequest request = GetIamPolicyRequest.newBuilder()
                .setResource(resource)
                .build();
        return projectsClient.getIamPolicy(request);

    }



}
