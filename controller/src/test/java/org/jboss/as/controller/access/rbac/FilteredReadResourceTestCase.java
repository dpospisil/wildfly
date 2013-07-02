package org.jboss.as.controller.access.rbac;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.constraint.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.constraint.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.jboss.dmr.ModelNode;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.jboss.as.controller.PathAddress.EMPTY_ADDRESS;
import static org.jboss.as.controller.PathAddress.pathAddress;
import static org.jboss.as.controller.PathElement.pathElement;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
public class FilteredReadResourceTestCase extends AbstractControllerTestBase {
    public static final String UNCONSTRAINED_RESOURCE = "unconstrained-resource";
    public static final String SENSITIVE_CONSTRAINED_RESOURCE = "sensitive-constrained-resource";

    public static final String FOO = "foo";
    public static final String BAR = "bar";

    @Before
    public void setup() {
        ModelNode operation = Util.createOperation(ADD, pathAddress(UNCONSTRAINED_RESOURCE, FOO));
        executeWithRoles(operation, StandardRole.SUPERUSER);
        operation = Util.createOperation(ADD, pathAddress(UNCONSTRAINED_RESOURCE, BAR));
        executeWithRoles(operation, StandardRole.SUPERUSER);

        operation = Util.createOperation(ADD, pathAddress(SENSITIVE_CONSTRAINED_RESOURCE, FOO));
        executeWithRoles(operation, StandardRole.SUPERUSER);
        operation = Util.createOperation(ADD, pathAddress(SENSITIVE_CONSTRAINED_RESOURCE, BAR));
        executeWithRoles(operation, StandardRole.SUPERUSER);
    }

    @Test
    public void testMonitor() {
        test(false, StandardRole.MONITOR);
    }

    @Test
    public void testOperator() {
        test(false, StandardRole.OPERATOR);
    }

    @Test
    public void testMaintainer() {
        test(false, StandardRole.MAINTAINER);
    }

    @Test
    public void testAdministrator() {
        test(true, StandardRole.ADMINISTRATOR);
    }

    @Test
    public void testAuditor() {
        test(true, StandardRole.AUDITOR);
    }

    @Test
    public void testSuperuser() {
        test(true, StandardRole.SUPERUSER);
    }

    @Test
    @Ignore("ManagementPermissionCollection.implies doesn't work in case of users with multiple roles")
    public void testMonitorOperator() {
        test(false, StandardRole.MONITOR, StandardRole.OPERATOR);
    }

    @Test
    @Ignore("ManagementPermissionCollection.implies doesn't work in case of users with multiple roles")
    public void testMonitorAdministrator() {
        test(true, StandardRole.MONITOR, StandardRole.ADMINISTRATOR);
    }

    @Test
    @Ignore("ManagementPermissionCollection.implies doesn't work in case of users with multiple roles")
    public void testAdministratorAuditor() {
        test(true, StandardRole.ADMINISTRATOR, StandardRole.AUDITOR);
    }

    private void test(boolean sensitiveResourceVisible, StandardRole... roles) {
        ModelNode operation = Util.createOperation(READ_RESOURCE_OPERATION, EMPTY_ADDRESS);
        operation.get(RECURSIVE).set(true);
        ModelNode result = executeWithRoles(operation, roles);

        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertTrue(result.get(RESULT, UNCONSTRAINED_RESOURCE).has(FOO));
        assertTrue(result.get(RESULT, UNCONSTRAINED_RESOURCE).has(BAR));
        assertEquals(sensitiveResourceVisible, result.get(RESULT, SENSITIVE_CONSTRAINED_RESOURCE).has(FOO));
        assertEquals(sensitiveResourceVisible, result.get(RESULT, SENSITIVE_CONSTRAINED_RESOURCE).has(BAR));

        // TODO is this format stable? testing it isn't that important anyway...
        assertEquals(!sensitiveResourceVisible, result.get(RESPONSE_HEADERS, "access-control").get(0)
                .get("hidden-children-types").get(0).asString().equals(SENSITIVE_CONSTRAINED_RESOURCE));
    }

    // test utils

    private ModelNode executeWithRoles(ModelNode operation, StandardRole... roles) {
        for (StandardRole role : roles) {
            operation.get(OPERATION_HEADERS, "roles").add(role.name());
        }
        return getController().execute(operation, null, null, null);
    }

    // model definition

    private static final SensitivityClassification MY_SENSITIVITY
            = new SensitivityClassification("test", "my-sensitivity", true, true, true);
    private static final AccessConstraintDefinition MY_SENSITIVE_CONSTRAINT
            = new SensitiveTargetAccessConstraintDefinition(MY_SENSITIVITY);

    @Override
    protected void initModel(Resource rootResource, ManagementResourceRegistration registration) {
        GlobalOperationHandlers.registerGlobalOperations(registration, ProcessType.EMBEDDED_SERVER);

        registration.registerSubModel(new TestResourceDefinition(UNCONSTRAINED_RESOURCE));
        registration.registerSubModel(new TestResourceDefinition(SENSITIVE_CONSTRAINED_RESOURCE,
                MY_SENSITIVE_CONSTRAINT));
    }

    private static final class TestResourceDefinition extends SimpleResourceDefinition {
        private final List<AccessConstraintDefinition> constraintDefinitions;

        TestResourceDefinition(String path, AccessConstraintDefinition... constraintDefinitions) {
            super(pathElement(path),
                    new NonResolvingResourceDescriptionResolver(),
                    new AbstractAddStepHandler() {
                        @Override
                        protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
                            // no-op
                        }
                    },
                    new AbstractRemoveStepHandler() {}
            );

            this.constraintDefinitions = Collections.unmodifiableList(Arrays.asList(constraintDefinitions));
        }

        @Override
        public List<AccessConstraintDefinition> getAccessConstraints() {
            return constraintDefinitions;
        }
    }
}
