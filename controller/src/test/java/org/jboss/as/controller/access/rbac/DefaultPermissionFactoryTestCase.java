package org.jboss.as.controller.access.rbac;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.access.*;
import org.jboss.as.controller.access.constraint.Constraint;
import org.jboss.as.controller.access.constraint.ConstraintFactory;
import org.jboss.as.controller.access.permission.CombinationPolicy;
import org.jboss.dmr.ModelNode;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
public class DefaultPermissionFactoryTestCase {
    private Caller caller;
    private Environment environment;

    @Before
    public void setUp() {
        caller = new Caller();
        ControlledProcessState processState = new ControlledProcessState(false);
        processState.setRunning();
        environment = new Environment(processState, ProcessType.EMBEDDED_SERVER);
    }

    @Test
    public void testSingleRoleRejectingCombinationPolicy() {
        testResourceSingleRole(CombinationPolicy.REJECTING, StandardRole.MONITOR, StandardRole.MONITOR, true);
        testResourceSingleRole(CombinationPolicy.REJECTING, StandardRole.MONITOR, StandardRole.OPERATOR, false);

        testAttributeSingleRole(CombinationPolicy.REJECTING, StandardRole.MONITOR, StandardRole.MONITOR, true);
        testAttributeSingleRole(CombinationPolicy.REJECTING, StandardRole.MONITOR, StandardRole.OPERATOR, false);
    }

    @Test
    public void testSingleRolePermissiveCombinationPolicy() {
        testResourceSingleRole(CombinationPolicy.PERMISSIVE, StandardRole.MONITOR, StandardRole.MONITOR, true);
        testResourceSingleRole(CombinationPolicy.PERMISSIVE, StandardRole.MONITOR, StandardRole.OPERATOR, false);

        testAttributeSingleRole(CombinationPolicy.PERMISSIVE, StandardRole.MONITOR, StandardRole.MONITOR, true);
        testAttributeSingleRole(CombinationPolicy.PERMISSIVE, StandardRole.MONITOR, StandardRole.OPERATOR, false);
    }

    @Test
    public void testSingleUserRoleMoreAllowedRoles() {
        testResource(CombinationPolicy.PERMISSIVE, new StandardRole[] {StandardRole.MONITOR},
                new StandardRole[] {StandardRole.MONITOR, StandardRole.ADMINISTRATOR}, true);
        testResource(CombinationPolicy.PERMISSIVE, new StandardRole[] {StandardRole.MONITOR},
                new StandardRole[] {StandardRole.OPERATOR, StandardRole.ADMINISTRATOR}, false);

        testAttribute(CombinationPolicy.PERMISSIVE, new StandardRole[]{StandardRole.MONITOR},
                new StandardRole[]{StandardRole.MONITOR, StandardRole.ADMINISTRATOR}, true);
        testAttribute(CombinationPolicy.PERMISSIVE, new StandardRole[]{StandardRole.MONITOR},
                new StandardRole[]{StandardRole.OPERATOR, StandardRole.ADMINISTRATOR}, false);
    }

    @Test
    @Ignore("ManagementPermissionCollection.implies doesn't work in case of users with multiple roles")
    public void testMoreUserRolesSingleAllowedRole() {
        testResource(CombinationPolicy.PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.MONITOR}, true);
        testResource(CombinationPolicy.PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.OPERATOR}, true);
        testResource(CombinationPolicy.PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.ADMINISTRATOR}, false);

        testAttribute(CombinationPolicy.PERMISSIVE, new StandardRole[]{StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[]{StandardRole.MONITOR}, true);
        testAttribute(CombinationPolicy.PERMISSIVE, new StandardRole[]{StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[]{StandardRole.OPERATOR}, true);
        testAttribute(CombinationPolicy.PERMISSIVE, new StandardRole[]{StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[]{StandardRole.ADMINISTRATOR}, false);
    }

    @Test
    @Ignore("ManagementPermissionCollection.implies doesn't work in case of users with multiple roles")
    public void testMoreUserRolesMoreAllowedRoles() {
        testResource(CombinationPolicy.PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR}, true);
        testResource(CombinationPolicy.PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.OPERATOR, StandardRole.ADMINISTRATOR}, true);
        testResource(CombinationPolicy.PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.ADMINISTRATOR, StandardRole.AUDITOR}, false);

        testAttribute(CombinationPolicy.PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR}, true);
        testAttribute(CombinationPolicy.PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.OPERATOR, StandardRole.ADMINISTRATOR}, true);
        testAttribute(CombinationPolicy.PERMISSIVE, new StandardRole[] {StandardRole.MONITOR, StandardRole.OPERATOR},
                new StandardRole[] {StandardRole.ADMINISTRATOR, StandardRole.AUDITOR}, false);
    }

    private void testResourceSingleRole(CombinationPolicy combinationPolicy, StandardRole userRole, StandardRole allowedRole,
                                        boolean accessExpectation) {
        testResource(combinationPolicy, new StandardRole[] {userRole}, new StandardRole[] {allowedRole}, accessExpectation);
    }

    private void testAttributeSingleRole(CombinationPolicy combinationPolicy, StandardRole userRole, StandardRole allowedRole,
                                         boolean accessExpectation) {
        testAttribute(combinationPolicy, new StandardRole[] {userRole}, new StandardRole[] {allowedRole}, accessExpectation);
    }

    private void testResource(CombinationPolicy combinationPolicy,
                              StandardRole[] userRoles,
                              StandardRole[] allowedRoles,
                              boolean accessExpectation) {

        ConstraintFactory constraintFactory = new TestConstraintFactory(allowedRoles);
        TestRoleMapper roleMapper = new TestRoleMapper(userRoles);
        DefaultPermissionFactory permissionFactory = new DefaultPermissionFactory(combinationPolicy, roleMapper,
                Collections.singleton(constraintFactory));

        Action action = new Action(null, null, EnumSet.of(Action.ActionEffect.ACCESS));
        TargetResource targetResource = TargetResource.forStandalone(null, null);

        PermissionCollection userPermissions = permissionFactory.getUserPermissions(caller, environment, action, targetResource);
        PermissionCollection requiredPermissions = permissionFactory.getRequiredPermissions(action, targetResource);

        for (Permission requiredPermission : toSet(requiredPermissions)) {
            assertEquals(accessExpectation, userPermissions.implies(requiredPermission));
        }
    }

    private void testAttribute(CombinationPolicy combinationPolicy,
                               StandardRole[] userRoles,
                               StandardRole[] allowedRoles,
                               boolean accessExpectation) {

        ConstraintFactory constraintFactory = new TestConstraintFactory(allowedRoles);
        TestRoleMapper roleMapper = new TestRoleMapper(userRoles);
        DefaultPermissionFactory permissionFactory = new DefaultPermissionFactory(combinationPolicy, roleMapper,
                Collections.singleton(constraintFactory));

        Action action = new Action(null, null, EnumSet.of(Action.ActionEffect.ACCESS));
        TargetResource targetResource = TargetResource.forStandalone(null, null);
        TargetAttribute targetAttribute = new TargetAttribute(null, new ModelNode(), targetResource);

        PermissionCollection userPermissions = permissionFactory.getUserPermissions(caller, environment, action, targetAttribute);
        PermissionCollection requiredPermissions = permissionFactory.getRequiredPermissions(action, targetAttribute);

        for (Permission requiredPermission : toSet(requiredPermissions)) {
            assertEquals(accessExpectation, userPermissions.implies(requiredPermission));
        }
    }

    @Test
    public void testRoleCombinationRejecting() {
        Action action = new Action(null, null, EnumSet.of(Action.ActionEffect.ACCESS,
                Action.ActionEffect.READ_CONFIG));
        TargetResource targetResource = TargetResource.forStandalone(null, null);

        DefaultPermissionFactory permissionFactory = null;
        try {
            permissionFactory = new DefaultPermissionFactory(CombinationPolicy.REJECTING, new TestRoleMapper(),
                    Collections.<ConstraintFactory>emptySet());
            permissionFactory.getUserPermissions(caller, environment, action, targetResource);
        } catch (Exception e) {
            fail();
        }

        try {
            permissionFactory = new DefaultPermissionFactory(CombinationPolicy.REJECTING,
                    new TestRoleMapper(StandardRole.MONITOR), Collections.<ConstraintFactory>emptySet());
            permissionFactory.getUserPermissions(caller, environment, action, targetResource);
        } catch (Exception e) {
            fail();
        }

        permissionFactory = new DefaultPermissionFactory(CombinationPolicy.REJECTING,
                new TestRoleMapper(StandardRole.MONITOR, StandardRole.DEPLOYER));
        try {
            permissionFactory.getUserPermissions(caller, environment, action, targetResource);
            fail();
        } catch (Exception e) { /* expected */ }

        permissionFactory = new DefaultPermissionFactory(CombinationPolicy.REJECTING,
                new TestRoleMapper(StandardRole.MONITOR, StandardRole.DEPLOYER, StandardRole.AUDITOR),
                Collections.<ConstraintFactory>emptySet());
        try {
            permissionFactory.getUserPermissions(caller, environment, action, targetResource);
            fail();
        } catch (Exception e) { /* expected */ }
    }

    // ---

    private static Set<Permission> toSet(PermissionCollection permissionCollection) {
        Set<Permission> result = new HashSet<Permission>();
        Enumeration<Permission> elements = permissionCollection.elements();
        while (elements.hasMoreElements()) {
            result.add(elements.nextElement());
        }
        return Collections.unmodifiableSet(result);
    }

    private static final class TestRoleMapper implements RoleMapper {
        private final Set<String> roles;

        private TestRoleMapper(StandardRole... roles) {
            Set<String> stringRoles = new HashSet<String>();
            for (StandardRole role : roles) {
                stringRoles.add(role.name());
            }
            this.roles = Collections.unmodifiableSet(stringRoles);
        }

        @Override
        public Set<String> mapRoles(Caller caller, Environment callEnvironment, Action action, TargetAttribute attribute) {
            return roles;
        }

        @Override
        public Set<String> mapRoles(Caller caller, Environment callEnvironment, Action action, TargetResource resource) {
            return roles;
        }
    }

    private static final class TestConstraintFactory implements ConstraintFactory {
        private final Set<StandardRole> allowedRoles;

        private TestConstraintFactory(StandardRole... allowedRoles) {
            Set<StandardRole> roles = new HashSet<StandardRole>();
            for (StandardRole allowedRole : allowedRoles) {
                roles.add(allowedRole);
            }
            this.allowedRoles = Collections.unmodifiableSet(roles);
        }

        @Override
        public Constraint getStandardUserConstraint(StandardRole role, Action.ActionEffect actionEffect) {
            boolean allowed = allowedRoles.contains(role);
            return new TestConstraint(allowed, Constraint.ControlFlag.REQUIRED);
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetAttribute target) {
            return new TestConstraint(true, Constraint.ControlFlag.REQUIRED);
        }

        @Override
        public Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetResource target) {
            return new TestConstraint(true, Constraint.ControlFlag.REQUIRED);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TestConstraintFactory;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    private static final class TestConstraint implements Constraint {
        private final boolean allowed;
        private final ControlFlag controlFlag;

        private TestConstraint(boolean allowed, ControlFlag controlFlag) {
            this.allowed = allowed;
            this.controlFlag = controlFlag;
        }

        @Override
        public boolean violates(Constraint other) {
            if (other instanceof TestConstraint) {
                return this.allowed != ((TestConstraint) other).allowed;
            }
            return false;
        }

        @Override
        public ControlFlag getControlFlag() {
            return controlFlag;
        }

        @Override
        public int compareTo(Constraint o) {
            return 0;
        }
    }
}
