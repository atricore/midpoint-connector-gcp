# MidPoint GCP Connector

A MidPoint connector for managing Google Cloud Platform (GCP) Identity and Access Management (IAM) permissions and their assignments to principals (users, groups, and service accounts)
for a specific **GCP project**.
It allows to assign permissions to Google Workspace (GWS) users and groups, as well as GCP service accounts for a GPC Project, while providing caching and backup capabilities for policy management.

## Overview

This connector allows MidPoint to:
- Read GCP IAM permissions/roles (standard, project-level, and organization-level)
- Manage role assignments for Google Workspace (GWS) users and groups
- Cache policy data for improved performance
- Backup policy changes for audit and recovery

## How Object Reconciliation Works

Understanding how objects are synchronized between MidPoint and GCP is crucial for proper configuration:

### GCP Roles (Import Process)
- **GCP Roles** are imported from GCP into MidPoint through scheduled import tasks
- These tasks can be run regularly to discover new roles or update existing ones
- Roles include standard GCP roles, project-level custom roles, and organization-level custom roles
- Once imported, GCP roles become available for assignment in MidPoint

### GCP Principals (Policy-Based Management)
- **GCP Principals** (GWS Accounts, GWS Groups) are not stored as separate objects in GCP
- Instead, GCP maintains a **Policy object** that contains role assignments (bindings)
- When a GCP role is assigned to a user or group in MidPoint:
  1. The connector reads the current GCP Policy object
  2. Updates the policy to add/remove the principal from the role's member list
  3. Writes the updated policy back to GCP
- Principals only "exist" in GCP as members within role bindings in the Policy object

### Synchronization Flow
```
MidPoint → GCP: Role assignments create/update policy bindings
GCP → MidPoint: Import tasks discover roles and existing assignments
```

This approach means that GCP doesn't store user or group objects directly - it only maintains the relationships between principals and roles through the centralized Policy object.

## Features

### Supported Object Types
- **GCP Roles**: Standard GCP roles, project custom roles, and organization custom roles
- **GWS Accounts**: Google Workspace user accounts
- **GWS Groups**: Google Workspace groups

### Supported Operations
- **Read/Search**: Query roles and principals with their current role assignments
- **Update**: Modify role bindings (add/remove users and groups from roles)
- **Create**: Limited creation of principal objects (returns UID based on name)
- **Test**: Connection validation

### Key Capabilities
- **Multi-level Role Support**: Handles standard, project, and organization-level roles
- **Policy Caching**: Configurable caching with TTL for improved performance
- **Policy Backup**: Automatic serialized backups of policy changes
- **Conditional Role Support**: Handles roles with conditions (sanitizes role names with `_withcond_` markers)
-
### Future Enhancements
- Support for custom role creation in a project
- Management of service account roles


## Prerequisites

### GCP Service Account Setup

1. **Create a Service Account** in your GCP project
2. **Generate a JSON key** for the service account
3. **Assign Required Permissions**:

   **At the Organization Level** (if using organization-level custom roles):
   - `roles/iam.organizationRoleViewer`

   **At the Project Level**:
   - `roles/iam.roleViewer`
   - `roles/resourcemanager.projectIamAdmin`

### Google Workspace Setup

Ensure your GCP project is linked to a Google Workspace domain if you plan to manage GWS users and groups.

## Installation

### Maven Dependencies

The connector uses the following key dependencies (managed via `pom.xml`):

```xml
<dependency>
    <groupId>com.google.cloud</groupId>
    <artifactId>google-iam-admin</artifactId>
</dependency>
<dependency>
    <groupId>com.google.cloud</groupId>
    <artifactId>google-cloud-resourcemanager</artifactId>
</dependency>
```

### Build the Connector

```bash
mvn clean package
```

This will create a JAR file in the `target/` directory that can be deployed to MidPoint.

## Configuration

### Required Configuration Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `project` | String | Yes | GCP Project ID |
| `serviceAccountKeyJson` | GuardedString | Yes | JSON key for the service account (confidential) |

### Optional Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `orgId` | Long | null | GCP Organization ID (required for org-level custom roles) |
| `allowCache` | Boolean | true | Enable/disable policy caching |
| `maxCacheTTL` | Long | 300000 | Maximum cache TTL in milliseconds (5 minutes) |
| `ignoreCacheAfterUpdateTTL` | Long | 5000 | Time to ignore cache after updates in milliseconds |
| `backupPolicy` | Boolean | false | Enable policy backup |
| `backupPolicyLocation` | String | null | Directory path for policy backups |

### Example Configuration in MidPoint

```xml
<resource>
    <name>GCP IAM Connector</name>
    <connectorRef type="ConnectorType">
        <connectorType>com.atricore.iam.midpoint.connector.gcp.GoogleCloudConnector</connectorType>
    </connectorRef>
    <connectorConfiguration>
        <project>your-gcp-project-id</project>
        <serviceAccountKeyJson>
            <clearValue>{
                "type": "service_account",
                "project_id": "your-project-id",
                "private_key_id": "...",
                "private_key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n",
                "client_email": "your-service-account@your-project.iam.gserviceaccount.com",
                "client_id": "...",
                "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                "token_uri": "https://oauth2.googleapis.com/token",
                "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
                "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/your-service-account%40your-project.iam.gserviceaccount.com"
            }</clearValue>
        </serviceAccountKeyJson>
        <orgId>123456789012</orgId>
        <allowCache>true</allowCache>
        <maxCacheTTL>300000</maxCacheTTL>
        <backupPolicy>true</backupPolicy>
        <backupPolicyLocation>/opt/midpoint/var/policy-backups</backupPolicyLocation>
    </connectorConfiguration>
</resource>
```

## Usage

### Schema Objects

The connector exposes the following object classes:

- **GCPRole**: Represents GCP IAM roles with attributes:
  - `name`: Role name (e.g., `roles/viewer`, `projects/my-project/roles/customRole`)
  - `title`: Human-readable role title
  - `description`: Role description
  - `USERS`: List of user members
  - `GROUPS`: List of group members
  - `SERVICE_ACCOUNTS`: List of service account members

- **GWSAccount**: Represents Google Workspace users
- **GWSGroup**: Represents Google Workspace groups
- **GCPServiceAccount**: Represents GCP service accounts

### Common Operations

#### Import GCP Roles
Configure an import task to discover all available GCP roles in your project and organization.

#### Assign Roles to Users/Groups
Use MidPoint's assignment mechanism to assign GCP roles to users or groups. The connector will update the GCP IAM policy accordingly.

#### Reconciliation
Run reconciliation tasks to sync changes between MidPoint and GCP IAM policies.

## Troubleshooting

### Common Issues

1. **Authentication Errors**
   - Verify the service account JSON key is correct
   - Ensure the service account has the required permissions
   - Check that the GCP project ID is correct

2. **Permission Denied**
   - Verify the service account has `roles/resourcemanager.projectIamAdmin` at the project level or organizatin level
   - For organization roles, ensure `roles/iam.organizationRoleViewer` at the org level
   - If you lose permissions to modify a project policy, you can assign IamAdmin at the organization level to regain access

3. **Cache Issues**
   - If seeing stale data, try disabling cache temporarily (`allowCache=false`)
   - Adjust cache TTL settings if needed
   - Check cache invalidation after updates

4. **Policy Backup Failures**
   - Ensure the backup directory exists and is writable
   - Check disk space in the backup location

### Logging

Enable debug logging for the connector package:
```xml
<logger name="com.atricore.iam.midpoint.connector.gcp" level="DEBUG"/>
```

## Known Limitations

- Custom role creation is not supported (roles are read-only)
- Query filter support is limited in search operations
- Delete operations are currently disabled for safety
- Complex query filters are not fully implemented
- Conditional role bindings are supported but conditions cannot be modified

## Testing

### Manual Testing
A test class is provided at `src/test/java/.../TestAPI.java` for manual API testing. Set the `GOOGLE_APPLICATION_CREDENTIALS` environment variable and run the test.

### Functional Testing
See `src/test/resources/TEST.md` for a comprehensive functional testing guide covering:
- Initial import scenarios
- Policy updates while unmanaged
- Policy updates while managed
- Reconciliation testing

## Support

For issues and questions:
1. Check the MidPoint logs for detailed error messages
2. Verify GCP service account permissions
3. Test the connection using the connector's test operation
4. Review the functional testing guide for common scenarios

## Version History

- **1.3.0-SNAPSHOT**: Current development version
- Based on ConnId framework 1.4 compatibility
- Uses Google Cloud Java SDK v26.34.0
