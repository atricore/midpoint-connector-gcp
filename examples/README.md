# MidPoint GCP Connector - Configuration Examples

This folder contains MidPoint resource configuration examples for the GCP Connector, demonstrating a template-based approach for managing multiple GCP projects efficiently.

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

## Template Architecture

The configuration uses MidPoint's resource template inheritance feature to provide a reusable base configuration that can be extended for specific GCP projects.

### Files Overview

- **`resource-gcp-template-out.xml`** - Base template resource (abstract/template)
- **`resource_gcp-my-project.xml`** - Example project-specific resource extending the template

## Base Template: `resource-gcp-template-out.xml`

### Template Features

The base template (`oid="10000000-0000-0000-0001-000000000199"`) provides:

- **Abstract Template**: Marked as `<abstract>true</abstract>` and `<template>true</template>`
- **Complete Schema Handling**: Defines mappings for all supported object types
- **Association Definitions**: Configures relationships between GCP roles and principals
- **Capability Configuration**: Sets up supported operations for each object type

### Object Type Mappings

#### 1. GCP Roles (`entitlement/group`)
- **Source**: `ri:GCPRole` → **Target**: MidPoint `RoleType`
- **Naming Convention**: `GCP:{project}:{role-name}`
- **Attributes Mapped**:
  - `icfs:name` → Role name and identifier
  - `description` → Role description
  - `ri:title` → Display name with project suffix
  - `ri:project` → Cost center
- **Capabilities**: Read-only (no create/delete), update enabled for membership changes

#### 2. GWS Groups (`account/GWSGroup`)
- **Source**: `ri:GWSGroup` → **Target**: MidPoint `RoleType`
- **Correlation**: By name (lowercase)
- **Capabilities**: Create/read/update enabled, no delete
- **Association**: Links to GCP roles via `ri:__GROUPS__`

#### 3. GWS Accounts (`account/GWSAccount`)
- **Source**: `ri:GWSAccount` → **Target**: MidPoint `UserType`
- **Correlation**: By name (lowercase)
- **Capabilities**: Create/read/update enabled, no delete
- **Association**: Links to GCP roles via `ri:__GROUPS__`

#### 4. GCP Service Accounts (`account/GCPServiceAccount`)
- **Source**: `ri:GCPServiceAccount` → **Target**: MidPoint `AccountType`
- **Status**: Basic configuration (minimal implementation)

### Association Types

#### GCP Role to GWS Group Association
```xml
<associationType>
    <name>roleToGWSGroup</name>
    <!-- Maps GWS Groups to GCP Roles via ri:__GROUPS__ -->
</associationType>
```

#### GCP Role to GWS Account Association
```xml
<associationType>
    <name>roleToGWSAccount</name>
    <!-- Maps GWS Accounts to GCP Roles via ri:__GROUPS__ -->
</associationType>
```

### Configuration Properties

The template includes default values for:
- **Organization ID**: `1234567890123` (placeholder)
- **Project ID**: `my-gcp-project` (placeholder)
- **Service Account Key**: Template JSON structure
- **Cache Settings**: 30-second TTL, cache enabled
- **Backup**: Disabled by default

## Project-Specific Resource: `resource_gcp-my-project.xml`

### Inheritance Pattern

The project-specific resource demonstrates the inheritance approach:

```xml
<resource oid="10000000-0000-0000-0002-000000000327">
    <name>GCP : my-gcp-project</name>
    <super>
        <resourceRef oid="10000000-0000-0000-0001-000000000199"/>
    </super>
    <connectorConfiguration>
        <!-- Override only project-specific settings -->
        <icfcga:project>my-gcp-project</icfcga:project>
    </connectorConfiguration>
</resource>
```

### Key Benefits

1. **Minimal Configuration**: Only project-specific values need to be specified
2. **Consistency**: All projects inherit the same schema handling and capabilities
3. **Maintainability**: Updates to the template automatically apply to all projects
4. **Standardization**: Ensures consistent naming conventions and mappings

## Usage Instructions

### Step 1: Deploy the Base Template

1. Import `resource-gcp-template-out.xml` into MidPoint
2. Update the template configuration:
   - Replace placeholder service account credentials
   - Adjust organization ID if using org-level roles
   - Configure backup settings as needed

### Step 2: Create Project-Specific Resources

For each GCP project you want to manage:

1. Copy `resource_gcp-my-project.xml` as a starting point
2. Update the following values:
   - **OID**: Generate a unique OID for the resource
   - **Name**: `GCP : {your-project-id}`
   - **Description**: Descriptive text for the project
   - **Project ID**: Replace `my-gcp-project` with your actual project ID
3. Optionally override other configuration properties if needed

### Step 3: Configure Synchronization Tasks

Create import/reconciliation tasks for each project resource:

- **GCP Roles Import**: Discover and import all available roles
- **GWS Groups Import**: Import Google Workspace groups
- **GWS Accounts Import**: Import Google Workspace users
- **Reconciliation Tasks**: Keep MidPoint and GCP in sync

## Advanced Configuration

### Archetype Filtering

Uncomment and configure archetype references to filter objects by type:

```xml
<!-- For GWS Groups -->
<archetypeRef oid="00000000-0000-0001-0328-000000000141"
              relation="org:default" type="c:ArchetypeType"/>
```

### Cache Tuning

Adjust cache settings in the template based on your environment:

```xml
<icfcga:maxCacheTTL>30000</icfcga:maxCacheTTL>  <!-- 30 seconds -->
<icfcga:allowCache>true</icfcga:allowCache>
```

## Security Considerations

### Service Account Permissions

Ensure each project's service account has the required permissions:
- `roles/iam.roleViewer` (project level)
- `roles/resourcemanager.projectIamAdmin` (project level)
- `roles/iam.organizationRoleViewer` (org level, if using org roles)

### Credential Management

- Store service account keys securely in MidPoint
- Use different service accounts for different projects when possible
- Regularly rotate service account keys

### Policy Backup

Enable policy backup for production environments:

```xml
<icfcga:backupPolicy>true</icfcga:backupPolicy>
<icfcga:backupPolicyLocation>/opt/midpoint/var/policy-backups</icfcga:backupPolicyLocation>
```

## Troubleshooting

### Template Inheritance Issues

- Verify the template resource is imported and active
- Check that the `<super>` reference uses the correct template OID
- Ensure the template is marked as `<abstract>true</abstract>`

### Project-Specific Overrides

- Only override necessary configuration properties
- Test connectivity after creating project-specific resources
- Verify that project-specific service account credentials are correct

### Association Problems

- Check that association mappings are properly inherited from the template
- Verify that the connector supports the association types being used
- Review synchronization reactions for proper handling of linked/unlinked objects

## Best Practices

1. **Template Maintenance**: Keep the base template updated with improvements
2. **Naming Conventions**: Use consistent naming patterns for all project resources
3. **Documentation**: Document any project-specific customizations
4. **Testing**: Test template changes in a development environment first
5. **Monitoring**: Set up monitoring for synchronization tasks across all projects

This template approach significantly reduces configuration overhead when managing multiple GCP projects while ensuring consistency and maintainability across your MidPoint deployment.
