/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.platform.routing.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants.ATTACHED_DOCUMENTS_PROPERTY_NAME;
import static org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants.DOCUMENT_ROUTE_DOCUMENT_TYPE;
import static org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants.EXECUTION_TYPE_PROPERTY_NAME;
import static org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants.ExecutionTypeValues.graph;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.ClientRuntimeException;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.impl.UserPrincipal;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.TransactionalFeature;
import org.nuxeo.ecm.platform.routing.api.DocumentRoute;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.ecm.platform.routing.api.operation.BulkRestartWorkflow;
import org.nuxeo.ecm.platform.routing.core.impl.GraphNode;
import org.nuxeo.ecm.platform.routing.core.impl.GraphNode.State;
import org.nuxeo.ecm.platform.routing.core.impl.GraphRoute;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskService;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.test.runner.RuntimeHarness;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, CoreFeature.class,
        AutomationFeature.class })
@Deploy({
        "org.nuxeo.ecm.platform.content.template", //
        "org.nuxeo.ecm.automation.core", //
        "org.nuxeo.ecm.directory", //
        "org.nuxeo.ecm.platform.usermanager", //
        "org.nuxeo.ecm.directory.types.contrib", //
        "org.nuxeo.ecm.directory.sql", //
        "org.nuxeo.ecm.platform.userworkspace.core", //
        "org.nuxeo.ecm.platform.userworkspace.types", //
        "org.nuxeo.ecm.platform.task.api", //
        "org.nuxeo.ecm.platform.task.core", //
        "org.nuxeo.ecm.platform.task.testing",
        "org.nuxeo.ecm.platform.routing.api",
        "org.nuxeo.ecm.platform.routing.core" //
})
@LocalDeploy({
        "org.nuxeo.ecm.platform.routing.core:OSGI-INF/test-graph-operations-contrib.xml",
        "org.nuxeo.ecm.platform.routing.core:OSGI-INF/test-graph-types-contrib.xml" })
public class GraphRouteTest {

    protected static final String TYPE_ROUTE_NODE = "RouteNode";

    @Inject
    protected FeaturesRunner featuresRunner;

    @Inject
    protected RuntimeHarness harness;

    @Inject
    protected CoreSession session;

    @Inject
    protected DocumentRoutingService routing;

    // init userManager now for early user tables creation (cleaner debug)
    @Inject
    protected UserManager userManager;

    @Inject
    protected TaskService taskService;

    @Inject
    protected AutomationService automationService;

    // a doc, associated to the route
    protected DocumentModel doc;

    // the route model we'll use
    protected DocumentModel routeDoc;

    @Before
    public void setUp() throws Exception {
        assertNotNull(routing);

        doc = session.createDocumentModel("/", "file", "File");
        doc.setPropertyValue("dc:title", "file");
        doc = session.createDocument(doc);

        routeDoc = createRoute("myroute");
    }

    @After
    public void tearDown() {
        // breakpoint here to examine database after test
    }

    protected CoreSession openSession(NuxeoPrincipal principal)
            throws ClientException {
        CoreFeature coreFeature = featuresRunner.getFeature(CoreFeature.class);
        Map<String, Serializable> ctx = new HashMap<String, Serializable>();
        ctx.put("principal", principal);
        return coreFeature.getRepository().getRepositoryHandler().openSession(
                ctx);
    }

    protected void closeSession(CoreSession session) {
        CoreInstance.getInstance().close(session);
    }

    protected DocumentModel createRoute(String name) throws ClientException,
            PropertyException {
        DocumentModel route = session.createDocumentModel("/", name,
                DOCUMENT_ROUTE_DOCUMENT_TYPE);
        route.setPropertyValue(EXECUTION_TYPE_PROPERTY_NAME, graph.name());
        route.setPropertyValue("dc:title", name);
        route.setPropertyValue(ATTACHED_DOCUMENTS_PROPERTY_NAME,
                (Serializable) Collections.singletonList(doc.getId()));
        return session.createDocument(route);
    }

    protected DocumentModel createNode(DocumentModel route, String name)
            throws ClientException, PropertyException {
        DocumentModel node = session.createDocumentModel(
                route.getPathAsString(), name, TYPE_ROUTE_NODE);
        node.setPropertyValue(GraphNode.PROP_NODE_ID, name);
        return session.createDocument(node);
    }

    protected Map<String, Serializable> transition(String name, String target,
            String condition) throws ClientException {
        Map<String, Serializable> m = new HashMap<String, Serializable>();
        m.put(GraphNode.PROP_TRANS_NAME, name);
        m.put(GraphNode.PROP_TRANS_TARGET, target);
        m.put(GraphNode.PROP_TRANS_CONDITION, condition);
        return m;
    }

    protected Map<String, Serializable> transition(String name, String target,
            String condition, String chainId) throws ClientException {
        Map<String, Serializable> m = transition(name, target, condition);
        m.put(GraphNode.PROP_TRANS_CHAIN, chainId);
        return m;
    }

    protected void setTransitions(DocumentModel node,
            Map<String, Serializable>... transitions) throws ClientException {
        node.setPropertyValue(GraphNode.PROP_TRANSITIONS,
                (Serializable) Arrays.asList(transitions));
    }

    protected Map<String, Serializable> button(String name, String label,
            String filter) {
        Map<String, Serializable> m = new HashMap<String, Serializable>();
        m.put(GraphNode.PROP_BTN_NAME, name);
        m.put(GraphNode.PROP_BTN_LABEL, label);
        m.put(GraphNode.PROP_BTN_FILTER, filter);
        return m;
    }

    protected void setButtons(DocumentModel node,
            Map<String, Serializable>... buttons) throws ClientException {
        node.setPropertyValue(GraphNode.PROP_TASK_BUTTONS,
                (Serializable) Arrays.asList(buttons));
    }

    protected DocumentRoute instantiateAndRun() throws ClientException {
        return instantiateAndRun(session, null);
    }

    protected DocumentRoute instantiateAndRun(CoreSession session)
            throws ClientException {
        return instantiateAndRun(session, null);
    }

    protected DocumentRoute instantiateAndRun(CoreSession session,
            Map<String, Serializable> map) throws ClientException {
        // route model
        DocumentRoute route = routeDoc.getAdapter(DocumentRoute.class);
        // draft -> validated
        if (!route.isValidated()) {
            route = routing.validateRouteModel(route, session);
        }
        // create instance and start
        String id = routing.createNewInstance(route.getDocument().getName(),
                Collections.singletonList(doc.getId()), map, session, true);
        return session.getDocument(new IdRef(id)).getAdapter(
                DocumentRoute.class);
    }

    @Test
    public void testExceptionIfNoStartNode() throws Exception {
        // route
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1 = session.saveDocument(node1);
        try {
            instantiateAndRun();
            fail("Should throw because no start node");
        } catch (ClientRuntimeException e) {
            String msg = e.getMessage();
            assertTrue(msg, msg.contains("No start node for graph"));
        }
    }

    @Test
    public void testExceptionIfNoTrueTransition() throws Exception {
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        node1 = session.saveDocument(node1);
        try {
            instantiateAndRun();
            fail("Should throw because no transition is true");
        } catch (ClientRuntimeException e) {
            String msg = e.getMessage();
            assertTrue(msg, msg.contains("No transition evaluated to true"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExceptionIfTransitionIsNotBoolean() throws Exception {
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(node1, transition("trans1", "node1", "'notaboolean'"));
        node1 = session.saveDocument(node1);
        try {
            instantiateAndRun();
            fail("Should throw because transition condition is no bool");
        } catch (ClientRuntimeException e) {
            String msg = e.getMessage();
            assertTrue(msg, msg.contains("does not evaluate to a boolean"));
        }
    }

    @Test
    public void testOneNodeStartStop() throws Exception {
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        node1.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node1 = session.saveDocument(node1);
        DocumentRoute route = instantiateAndRun();
        assertTrue(route.isDone());
    }

    @Test
    public void testStartWithMap() throws Exception {
        routeDoc.setPropertyValue(GraphRoute.PROP_VARIABLES_FACET,
                "FacetRoute1");
        routeDoc = session.saveDocument(routeDoc);
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        node1.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node1 = session.saveDocument(node1);
        Map<String, Serializable> map = new HashMap<String, Serializable>();
        map.put("stringfield", "ABC");
        DocumentRoute route = instantiateAndRun(session, map);
        assertTrue(route.isDone());
        String v = (String) route.getDocument().getPropertyValue(
                "fctroute1:stringfield");
        assertEquals("ABC", v);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExceptionIfLooping() throws Exception {
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(node1, transition("trans1", "node1", "true"));
        node1 = session.saveDocument(node1);
        try {
            instantiateAndRun();
            fail("Should throw because execution is looping");
        } catch (ClientRuntimeException e) {
            String msg = e.getMessage();
            assertTrue(msg, msg.contains("Execution is looping"));
        }
    }

    @Test
    public void testAutomationChains() throws Exception {
        assertEquals("file", doc.getTitle());
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        node1.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node1.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_title1");
        node1.setPropertyValue(GraphNode.PROP_OUTPUT_CHAIN, "testchain_title2");
        node1 = session.saveDocument(node1);
        DocumentRoute route = instantiateAndRun();
        assertTrue(route.isDone());
        doc.refresh();
        assertEquals("title 2", doc.getTitle());
    }

    @Test
    public void testAutomationChainVariableChange() throws Exception {
        // route model var
        routeDoc.setPropertyValue(GraphRoute.PROP_VARIABLES_FACET,
                "FacetRoute1");
        routeDoc = session.saveDocument(routeDoc);
        // node model
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        node1.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node1.setPropertyValue(GraphNode.PROP_INPUT_CHAIN,
                "testchain_stringfield");
        node1.setPropertyValue(GraphNode.PROP_OUTPUT_CHAIN,
                "testchain_stringfield2");
        // node model var
        node1.setPropertyValue(GraphNode.PROP_VARIABLES_FACET, "FacetNode1");
        node1 = session.saveDocument(node1);
        DocumentRoute route = instantiateAndRun();
        assertTrue(route.isDone());

        // check route instance var
        DocumentModel r = route.getDocument();
        String s = (String) r.getPropertyValue("fctroute1:stringfield");
        assertEquals("foo", s);
        // Calendar d = (Calendar) r.getPropertyValue("datefield");
        // assertEquals("XXX", d);

        // check node instance var
        // must be admin to get children, due to rights restrictions
        NuxeoPrincipal admin = new UserPrincipal("admin", null, false, true);
        CoreSession ses = openSession(admin);
        DocumentModel c = ses.getChildren(r.getRef()).get(0);
        s = (String) c.getPropertyValue("stringfield2");
        assertEquals("bar", s);
        closeSession(ses);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSimpleTransition() throws Exception {
        assertEquals("file", doc.getTitle());
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(node1,
                transition("trans1", "node2", "true", "testchain_title1"),
                transition("trans2", "node2", "false", "testchain_title2"));
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node2 = session.saveDocument(node2);

        DocumentRoute route = instantiateAndRun();

        assertTrue(route.isDone());
        doc.refresh();
        assertEquals("title 1", doc.getTitle());

        // check start/end dates and counts
        DocumentModel doc1 = ((GraphRoute) route).getNode("node1").getDocument();
        assertEquals(Long.valueOf(1), doc1.getPropertyValue("rnode:count"));
        assertNotNull(doc1.getPropertyValue("rnode:startDate"));
        assertNotNull(doc1.getPropertyValue("rnode:endDate"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testResume() throws Exception {
        assertEquals("file", doc.getTitle());
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(node1,
                transition("trans12", "node2", "true", "testchain_title1"));
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        setTransitions(node2, transition("trans23", "node3", "true"));
        node2 = session.saveDocument(node2);

        DocumentModel node3 = createNode(routeDoc, "node3");
        node3.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node3 = session.saveDocument(node3);

        DocumentRoute route = instantiateAndRun();

        assertFalse(route.isDone());

        // now resume, as if the task was actually executed
        Map<String, Object> data = new HashMap<String, Object>();
        routing.resumeInstance(route.getDocument().getId(), "node2", data,
                null, session);

        route.getDocument().refresh();
        assertTrue(route.isDone());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCancel() throws Exception {
        assertEquals("file", doc.getTitle());
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(node1,
                transition("trans12", "node2", "true", "testchain_title1"));
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        setTransitions(node2, transition("trans23", "node3", "true"));
        node2 = session.saveDocument(node2);

        DocumentModel node3 = createNode(routeDoc, "node3");
        node3.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node3 = session.saveDocument(node3);

        DocumentRoute route = instantiateAndRun();

        assertFalse(route.isDone());

        List<Task> tasks = taskService.getTaskInstances(doc,
                (NuxeoPrincipal) null, session);
        assertEquals(1, tasks.size());

        route.cancel(session);
        route.getDocument().refresh();
        assertTrue(route.isCanceled());

        tasks = taskService.getTaskInstances(doc, (NuxeoPrincipal) null,
                session);
        assertEquals(0, tasks.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testForkMergeAll() throws Exception {
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(node1,
                transition("trans1", "node2", "true", "testchain_title1"),
                transition("trans2", "node2", "true", "testchain_descr1"));
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_MERGE, "all");
        node2.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_rights1");
        node2.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node2 = session.saveDocument(node2);

        DocumentRoute route = instantiateAndRun();

        assertTrue(route.isDone());
        doc.refresh();
        assertEquals("title 1", doc.getTitle());
        assertEquals("descr 1", doc.getPropertyValue("dc:description"));
        assertEquals("rights 1", doc.getPropertyValue("dc:rights"));
    }

    // a few more nodes before the merge
    @SuppressWarnings("unchecked")
    @Test
    public void testForkMergeAll2() throws Exception {
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(node1, transition("trans1", "node2", "true"),
                transition("trans2", "node3", "true"));
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_title1");
        setTransitions(node2, transition("trans1", "node4", "true"));
        node2 = session.saveDocument(node2);

        DocumentModel node3 = createNode(routeDoc, "node3");
        node3.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_descr1");
        setTransitions(node3, transition("trans2", "node4", "true"));
        node3 = session.saveDocument(node3);

        DocumentModel node4 = createNode(routeDoc, "node4");
        node4.setPropertyValue(GraphNode.PROP_MERGE, "all");
        node4.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_rights1");
        node4.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node4 = session.saveDocument(node4);

        DocumentRoute route = instantiateAndRun();

        assertTrue(route.isDone());
        doc.refresh();
        assertEquals("title 1", doc.getTitle());
        assertEquals("descr 1", doc.getPropertyValue("dc:description"));
        assertEquals("rights 1", doc.getPropertyValue("dc:rights"));
    }

    // a few more nodes before the merge
    @SuppressWarnings("unchecked")
    @Test
    public void testForkMergeAllWithTasks() throws Exception {
        NuxeoPrincipal user1 = userManager.getPrincipal("myuser1");
        assertNotNull(user1);

        NuxeoPrincipal user2 = userManager.getPrincipal("myuser2");
        assertNotNull(user2);

        NuxeoPrincipal user3 = userManager.getPrincipal("myuser3");
        assertNotNull(user3);

        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(node1, transition("trans1", "node2", "true"),
                transition("trans2", "node3", "true"),
                transition("trans3", "node4", "true"));
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_title1");
        setTransitions(node2, transition("trans1", "node5", "true"));

        node2.setPropertyValue(GraphNode.PROP_OUTPUT_CHAIN, "testchain_rights1");
        node2.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES_PERMISSION,
                "Write");
        node2.setPropertyValue(GraphNode.PROP_INPUT_CHAIN,
                "test_setGlobalvariable");
        node2.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        String[] users = { user1.getName() };
        node2.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES, users);
        setButtons(node2, button("btn1", "label-btn1", "filterrr"));
        node2 = session.saveDocument(node2);

        DocumentModel node3 = createNode(routeDoc, "node3");
        node3.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_descr1");
        setTransitions(node3, transition("trans2", "node5", "true"));

        node3.setPropertyValue(GraphNode.PROP_OUTPUT_CHAIN, "testchain_rights1");
        node3.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES_PERMISSION,
                "Write");
        node3.setPropertyValue(GraphNode.PROP_INPUT_CHAIN,
                "test_setGlobalvariable");
        node3.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        String[] users2 = { user2.getName() };
        node3.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES, users2);
        setButtons(node1, button("btn2", "label-btn2", "filterrr"));
        node3 = session.saveDocument(node3);

        DocumentModel node4 = createNode(routeDoc, "node4");
        node4.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_descr1");
        setTransitions(node4, transition("trans3", "node5", "true"));

        node4.setPropertyValue(GraphNode.PROP_OUTPUT_CHAIN, "testchain_rights1");
        node4.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES_PERMISSION,
                "Write");
        node4.setPropertyValue(GraphNode.PROP_INPUT_CHAIN,
                "test_setGlobalvariable");
        node4.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        String[] users3 = { user3.getName() };
        node4.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES, users3);
        setButtons(node1, button("btn2", "label-btn2", "filterrr"));
        node4 = session.saveDocument(node4);

        DocumentModel node5 = createNode(routeDoc, "node5");
        node5.setPropertyValue(GraphNode.PROP_MERGE, "all");
        node5.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_rights1");
        node5.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node5 = session.saveDocument(node5);

        DocumentRoute route = instantiateAndRun();

        List<Task> tasks = taskService.getTaskInstances(doc, user1, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        Map<String, Object> data = new HashMap<String, Object>();
        CoreSession sessionUser1 = openSession(user1);
        // task assignees have READ on the route instance
        assertNotNull(sessionUser1.getDocument(route.getDocument().getRef()));
        routing.endTask(sessionUser1, tasks.get(0), data, "trans1");
        closeSession(sessionUser1);

        tasks = taskService.getTaskInstances(doc, user2, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        data = new HashMap<String, Object>();
        CoreSession sessionUser2 = openSession(user2);
        // task assignees have READ on the route instance
        assertNotNull(sessionUser2.getDocument(route.getDocument().getRef()));
        routing.endTask(sessionUser2, tasks.get(0), data, "trans2");
        closeSession(sessionUser2);

        // end task and verify that route was done
        NuxeoPrincipal admin = new UserPrincipal("admin", null, false, true);
        session = openSession(admin);
        route = session.getDocument(route.getDocument().getRef()).getAdapter(
                DocumentRoute.class);
        assertFalse(route.isDone());

        tasks = taskService.getTaskInstances(doc, user3, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        data = new HashMap<String, Object>();
        CoreSession sessionUser3 = openSession(user3);
        // task assignees have READ on the route instance
        assertNotNull(sessionUser3.getDocument(route.getDocument().getRef()));
        routing.endTask(sessionUser3, tasks.get(0), data, "trans3");
        closeSession(sessionUser3);

        // end task and verify that route was done
        admin = new UserPrincipal("admin", null, false, true);
        closeSession(session);
        session = openSession(admin);
        route = session.getDocument(route.getDocument().getRef()).getAdapter(
                DocumentRoute.class);
        assertTrue(route.isDone());
        closeSession(session);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testForkMergeOne() throws Exception {
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(node1, transition("trans12", "node2", "true"),
                transition("trans13", "node3", "true"));
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_title1");
        setTransitions(node2, transition("trans25", "node5", "true"));
        node2 = session.saveDocument(node2);

        DocumentModel node3 = createNode(routeDoc, "node3");
        node3.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_descr1");
        setTransitions(node3, transition("trans34", "node4", "true"));
        node3 = session.saveDocument(node3);

        DocumentModel node4 = createNode(routeDoc, "node4");
        node4.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_descr2");
        setTransitions(node4, transition("trans45", "node5", "true"));
        node4 = session.saveDocument(node4);

        DocumentModel node5 = createNode(routeDoc, "node5");
        node5.setPropertyValue(GraphNode.PROP_MERGE, "one");
        node5.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_rights1");
        node5.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node5 = session.saveDocument(node5);

        DocumentRoute route = instantiateAndRun();

        assertTrue(route.isDone());

        doc.refresh();
        assertEquals("title 1", doc.getTitle());
        assertEquals("descr 1", doc.getPropertyValue("dc:description"));
        // didn't go up to descr 2, which was canceled
        assertEquals("rights 1", doc.getPropertyValue("dc:rights"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testForkMergeWithLoopTransition() throws Exception {
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(node1,
                transition("trans1", "node2", "true", "testchain_title1"),
                transition("trans2", "node2", "true", "testchain_descr1"));
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_MERGE, "all");
        node2.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_rights1");
        node2.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        setTransitions(node2, transition("transloop", "node1", "false"));
        node2 = session.saveDocument(node2);

        DocumentRoute route = instantiateAndRun();

        assertTrue(route.isDone());
        doc.refresh();
        assertEquals("title 1", doc.getTitle());
        assertEquals("descr 1", doc.getPropertyValue("dc:description"));
        assertEquals("rights 1", doc.getPropertyValue("dc:rights"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testForkMergeWithTasksAndLoopTransitions() throws Exception {

        NuxeoPrincipal user1 = userManager.getPrincipal("myuser1");
        assertNotNull(user1);

        NuxeoPrincipal user2 = userManager.getPrincipal("myuser2");
        assertNotNull(user2);

        // Create nodes
        DocumentModel startNode = createNode(routeDoc, "startNode");
        startNode.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(startNode,
                transition("transToParallel1", "parallelNode1", "true"),
                transition("transToParallel2", "parallelNode2", "true"));
        startNode = session.saveDocument(startNode);

        DocumentModel parallelNode1 = createNode(routeDoc, "parallelNode1");
        parallelNode1.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        String[] users1 = { user1.getName() };
        parallelNode1.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES, users1);
        setTransitions(
                parallelNode1,
                transition("transLoop", "parallelNode1",
                        "NodeVariables[\"button\"] ==\"loop\""),
                transition("transToMerge", "mergeNode",
                        "NodeVariables[\"button\"] ==\"toMerge\""));
        parallelNode1 = session.saveDocument(parallelNode1);

        DocumentModel parallelNode2 = createNode(routeDoc, "parallelNode2");
        parallelNode2.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        String[] users2 = { user2.getName() };
        parallelNode2.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES, users2);
        setTransitions(
                parallelNode2,
                transition("transLoop", "parallelNode2",
                        "NodeVariables[\"button\"] ==\"loop\""),
                transition("transToMerge", "mergeNode",
                        "NodeVariables[\"button\"] ==\"toMerge\""));
        parallelNode2 = session.saveDocument(parallelNode2);

        DocumentModel mergeNode = createNode(routeDoc, "mergeNode");
        mergeNode.setPropertyValue(GraphNode.PROP_MERGE, "all");
        mergeNode.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        mergeNode.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES, users1);
        setTransitions(
                mergeNode,
                transition("transLoop", "startNode",
                        "NodeVariables[\"button\"] ==\"loop\""),
                transition("transEnd", "endNode",
                        "NodeVariables[\"button\"] ==\"end\""));
        mergeNode = session.saveDocument(mergeNode);

        DocumentModel endNode = createNode(routeDoc, "endNode");
        endNode.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        endNode = session.saveDocument(endNode);

        // Start route
        DocumentRoute route = instantiateAndRun();

        // Make user1 end his parallel task (1st time)
        List<Task> tasks = taskService.getTaskInstances(doc, user1, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        Map<String, Object> data = new HashMap<String, Object>();
        CoreSession sessionUser1 = openSession(user1);
        routing.endTask(sessionUser1, tasks.get(0), data, "toMerge");
        closeSession(sessionUser1);

        // Make user2 end his parallel task (1st time)
        tasks = taskService.getTaskInstances(doc, user2, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        CoreSession sessionUser2 = openSession(user2);
        routing.endTask(sessionUser2, tasks.get(0), data, "toMerge");
        closeSession(sessionUser2);

        // Make user1 end the merge task choosing the "loop" transition
        tasks = taskService.getTaskInstances(doc, user1, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        sessionUser1 = openSession(user1);
        routing.endTask(sessionUser1, tasks.get(0), data, "loop");
        closeSession(sessionUser1);

        // Make user1 end his parallel task (2nd time)
        tasks = taskService.getTaskInstances(doc, user1, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        sessionUser1 = openSession(user1);
        routing.endTask(sessionUser1, tasks.get(0), data, "toMerge");
        closeSession(sessionUser1);

        // Make user2 end his parallel task (2nd time)
        tasks = taskService.getTaskInstances(doc, user2, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        sessionUser2 = openSession(user2);
        routing.endTask(sessionUser2, tasks.get(0), data, "toMerge");
        closeSession(sessionUser2);

        // Make user1 end the merge task choosing the "end" transition
        tasks = taskService.getTaskInstances(doc, user1, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        sessionUser1 = openSession(user1);
        routing.endTask(sessionUser1, tasks.get(0), data, "end");
        closeSession(sessionUser1);

        // Check that route is done
        session.save();
        route = session.getDocument(route.getDocument().getRef()).getAdapter(
                DocumentRoute.class);
        assertTrue(route.isDone());
    }

    @SuppressWarnings("unchecked")
    @Test
    @Ignore
    public void testForkWithLoopFromParallelToFork() throws Exception {

        NuxeoPrincipal user1 = userManager.getPrincipal("myuser1");
        assertNotNull(user1);

        NuxeoPrincipal user2 = userManager.getPrincipal("myuser2");
        assertNotNull(user2);

        // Create nodes
        DocumentModel startNode = createNode(routeDoc, "startNode");
        startNode.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        startNode.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        String[] users1 = { user1.getName() };
        startNode.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES, users1);
        setTransitions(
                startNode,
                transition("transToParallel1", "parallelNode1",
                        "NodeVariables[\"button\"] ==\"validate\""),
                transition("transToParallel2", "parallelNode2",
                        "NodeVariables[\"button\"] ==\"validate\""));
        startNode = session.saveDocument(startNode);

        DocumentModel parallelNode1 = createNode(routeDoc, "parallelNode1");
        parallelNode1.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        parallelNode1.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES, users1);
        setTransitions(
                parallelNode1,
                transition("transLoop", "startNode",
                        "NodeVariables[\"button\"] ==\"loop\""),
                transition("transToMerge", "mergeNode",
                        "NodeVariables[\"button\"] ==\"toMerge\""));
        parallelNode1 = session.saveDocument(parallelNode1);

        DocumentModel parallelNode2 = createNode(routeDoc, "parallelNode2");
        parallelNode2.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        String[] users2 = { user2.getName() };
        parallelNode2.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES, users2);
        setTransitions(
                parallelNode2,
                transition("transToMerge", "mergeNode",
                        "NodeVariables[\"button\"] ==\"toMerge\""));
        parallelNode2 = session.saveDocument(parallelNode2);

        DocumentModel mergeNode = createNode(routeDoc, "mergeNode");
        mergeNode.setPropertyValue(GraphNode.PROP_MERGE, "all");
        startNode.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        mergeNode = session.saveDocument(mergeNode);

        // Start route
        DocumentRoute route = instantiateAndRun();

        // Make user1 validate the start task (1st time)
        List<Task> tasks = taskService.getTaskInstances(doc, user1, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        Map<String, Object> data = new HashMap<String, Object>();
        CoreSession sessionUser1 = openSession(user1);
        routing.endTask(sessionUser1, tasks.get(0), data, "validate");
        closeSession(sessionUser1);

        // Make user1 loop to the start task
        tasks = taskService.getTaskInstances(doc, user1, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        data = new HashMap<String, Object>();
        sessionUser1 = openSession(user1);
        routing.endTask(sessionUser1, tasks.get(0), data, "loop");
        closeSession(sessionUser1);

        // Make user1 validate the start task (2nd time)
        tasks = taskService.getTaskInstances(doc, user1, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        data = new HashMap<String, Object>();
        sessionUser1 = openSession(user1);
        routing.endTask(sessionUser1, tasks.get(0), data, "validate");
        closeSession(sessionUser1);

        // Make user1 validate his parallel task
        tasks = taskService.getTaskInstances(doc, user1, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        data = new HashMap<String, Object>();
        sessionUser1 = openSession(user1);
        routing.endTask(sessionUser1, tasks.get(0), data, "toMerge");
        closeSession(sessionUser1);

        // Make user2 end his parallel task
        tasks = taskService.getTaskInstances(doc, user2, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        CoreSession sessionUser2 = openSession(user2);
        routing.endTask(sessionUser2, tasks.get(0), data, "toMerge");
        closeSession(sessionUser2);

        // Check that route is done
        session.save();
        route = session.getDocument(route.getDocument().getRef()).getAdapter(
                DocumentRoute.class);
        assertTrue(route.isDone());
    }

    // @SuppressWarnings("unchecked")
    // @Test
    public void testRouteWithTasks() throws Exception {

        NuxeoPrincipal user1 = userManager.getPrincipal("myuser1");
        assertNotNull(user1);

        routeDoc.setPropertyValue(GraphRoute.PROP_VARIABLES_FACET,
                "FacetRoute1");
        routeDoc.addFacet("FacetRoute1");
        routeDoc = session.saveDocument(routeDoc);
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_VARIABLES_FACET, "FacetNode1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(
                node1,
                transition("trans1", "node2",
                        "NodeVariables[\"button\"] == \"trans1\"",
                        "testchain_title1"));

        // task properties

        node1.setPropertyValue(GraphNode.PROP_OUTPUT_CHAIN, "testchain_rights1");
        node1.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES_PERMISSION,
                "Write");
        node1.setPropertyValue(GraphNode.PROP_INPUT_CHAIN,
                "test_setGlobalvariable");
        node1.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        node1.setPropertyValue(GraphNode.PROP_TASK_DOC_TYPE, "MyTaskDoc");
        String[] users = { user1.getName() };
        node1.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES, users);
        setButtons(node1, button("btn1", "label-btn1", "filterrr"));
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_MERGE, "all");

        node2.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node2 = session.saveDocument(node2);

        DocumentRoute route = instantiateAndRun();

        List<Task> tasks = taskService.getTaskInstances(doc, user1, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        Map<String, Object> data = new HashMap<String, Object>();
        CoreSession sessionUser1 = openSession(user1);
        // task assignees have READ on the route instance
        assertNotNull(sessionUser1.getDocument(route.getDocument().getRef()));
        Task task1 = tasks.get(0);
        assertEquals("MyTaskDoc", task1.getDocument().getType());
        List<DocumentModel> docs = routing.getWorkflowInputDocuments(
                sessionUser1, task1);
        assertEquals(doc.getId(), docs.get(0).getId());
        routing.endTask(sessionUser1, tasks.get(0), data, "trans1");
        closeSession(sessionUser1);
        // end task and verify that route was done
        NuxeoPrincipal admin = new UserPrincipal("admin", null, false, true);
        session = openSession(admin);
        route = session.getDocument(route.getDocument().getRef()).getAdapter(
                DocumentRoute.class);
        assertTrue(route.isDone());
        assertEquals(
                "test",
                route.getDocument().getPropertyValue("fctroute1:globalVariable"));
        closeSession(session);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEvaluateTaskAssigneesFromVariable() throws Exception {
        NuxeoPrincipal user1 = userManager.getPrincipal("myuser1");
        NuxeoPrincipal user2 = userManager.getPrincipal("myuser2");
        List<String> assignees = Arrays.asList(user1.getName(), user2.getName());

        routeDoc.setPropertyValue(GraphRoute.PROP_VARIABLES_FACET,
                "FacetRoute1");
        routeDoc.addFacet("FacetRoute1");
        routeDoc.setPropertyValue("fctroute1:myassignees",
                (Serializable) assignees);
        routeDoc = session.saveDocument(routeDoc);

        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_VARIABLES_FACET, "FacetNode1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        node1.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_rights1");
        node1.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES_PERMISSION,
                "Write");
        setTransitions(node1,
                transition("trans1", "node2", "true", "testchain_title1"));
        // add a workflow variables with name "myassignees"
        node1.setPropertyValue("rnode:taskAssigneesExpr",
                "WorkflowVariables[\"myassignees\"]");
        node1.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_MERGE, "all");
        node2.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node2 = session.saveDocument(node2);

        DocumentRoute route = instantiateAndRun();

        List<Task> tasks = taskService.getTaskInstances(doc, user1, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        Task ts = tasks.get(0);
        assertEquals(2, ts.getActors().size());

        // check permissions set during task
        assertTrue(session.hasPermission(user1, doc.getRef(), "Write"));
        assertTrue(session.hasPermission(user2, doc.getRef(), "Write"));

        // end task

        Map<String, Object> data = new HashMap<String, Object>();
        CoreSession sessionUser2 = openSession(user2);
        routing.endTask(sessionUser2, tasks.get(0), data, "trans1");
        closeSession(sessionUser2);

        // verify that route was done
        NuxeoPrincipal admin = new UserPrincipal("admin", null, false, true);
        session = openSession(admin);
        route = session.getDocument(route.getDocument().getRef()).getAdapter(
                DocumentRoute.class);
        assertTrue(route.isDone());

        // permissions are reset
        assertFalse(session.hasPermission(user1, doc.getRef(), "Write"));
        assertFalse(session.hasPermission(user2, doc.getRef(), "Write"));

        closeSession(session);
    }

    /**
     * Check that when running as non-Administrator the assignees are set
     * correctly.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testComputedTaskAssignees() throws Exception {
        NuxeoPrincipal user1 = userManager.getPrincipal("myuser1");
        NuxeoPrincipal user2 = userManager.getPrincipal("myuser2");

        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_VARIABLES_FACET, "FacetNode1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        node1.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_rights1");
        node1.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES_PERMISSION,
                "Write");
        setTransitions(node1,
                transition("trans1", "node2", "true", "testchain_title1"));
        // add a workflow node assignees expression
        node1.setPropertyValue("rnode:taskAssigneesExpr", "\"myuser1\"");
        node1.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_MERGE, "all");
        node2.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node2 = session.saveDocument(node2);

        session.save();

        // another session as user2
        CoreSession session2 = openSession(user2);

        DocumentRoute route = instantiateAndRun(session2);

        List<Task> tasks = taskService.getTaskInstances(doc, user1, session2);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        Task ts = tasks.get(0);
        assertEquals(1, ts.getActors().size());
        session2.save(); // flush invalidations
        closeSession(session2);

        // process task as user1
        CoreSession session1 = openSession(user1);
        try {
            routing.endTask(session1, tasks.get(0),
                    new HashMap<String, Object>(), "trans1");
        } finally {
            closeSession(session1);
        }

        // verify that route was done
        session.save(); // process invalidations
        route = session.getDocument(route.getDocument().getRef()).getAdapter(
                DocumentRoute.class);
        assertTrue(route.isDone());
        assertFalse(session.hasPermission(user1, doc.getRef(), "Write"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDynamicallyComputeDueDate() throws PropertyException,
            ClientException {
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_VARIABLES_FACET, "FacetNode1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        node1.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_rights1");
        node1.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES_PERMISSION,
                "Write");
        setTransitions(node1,
                transition("trans1", "node2", "true", "testchain_title1"));

        node1.setPropertyValue("rnode:taskAssigneesExpr", "\"Administrator\"");
        node1.setPropertyValue("rnode:taskDueDateExpr", "CurrentDate.days(1)");
        node1.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_MERGE, "all");
        node2.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node2 = session.saveDocument(node2);

        session.save();
        instantiateAndRun(session);

        List<Task> tasks = taskService.getTaskInstances(doc,
                (NuxeoPrincipal) session.getPrincipal(), session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        Task ts = tasks.get(0);
        Calendar currentDate = Calendar.getInstance();
        Calendar taskDueDate = Calendar.getInstance();
        taskDueDate.setTime(ts.getDueDate());
        int tomorrow = currentDate.get(Calendar.DAY_OF_YEAR) + 1;
        int due = taskDueDate.get(Calendar.DAY_OF_YEAR);
        if (due != 1) {
            assertEquals(tomorrow, due);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWorkflowInitiatorAndTaskActor() throws Exception {
        NuxeoPrincipal user1 = userManager.getPrincipal("myuser1");
        NuxeoPrincipal user2 = userManager.getPrincipal("myuser2");

        routeDoc.setPropertyValue(GraphRoute.PROP_VARIABLES_FACET,
                "FacetRoute1");
        routeDoc.addFacet("FacetRoute1");
        routeDoc = session.saveDocument(routeDoc);

        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_VARIABLES_FACET, "FacetNode1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(
                node1,
                transition("trans1", "node2", "true",
                        "test_setGlobalVariableToWorkflowInitiator"));
        node1.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        node1.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES,
                new String[] { user2.getName() });
        setButtons(node1, button("btn1", "label-btn1", "filterrr"));
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node2 = session.saveDocument(node2);
        session.save();

        // start workflow as user1

        CoreSession sessionUser1 = openSession(user1);
        DocumentRoute route = instantiateAndRun(sessionUser1);
        DocumentRef routeDocRef = route.getDocument().getRef();
        closeSession(sessionUser1);

        // check user2 tasks

        List<Task> tasks = taskService.getTaskInstances(doc, user2, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        // continue task as user2

        CoreSession sessionUser2 = openSession(user2);
        // task assignees have READ on the route instance
        assertNotNull(sessionUser2.getDocument(routeDocRef));
        Task task = tasks.get(0);
        List<DocumentModel> docs = routing.getWorkflowInputDocuments(
                sessionUser2, task);
        assertEquals(doc.getId(), docs.get(0).getId());
        Map<String, Object> data = new HashMap<String, Object>();
        routing.endTask(sessionUser2, tasks.get(0), data, "trans1");
        closeSession(sessionUser2);

        // verify things
        NuxeoPrincipal admin = new UserPrincipal("admin", null, false, true);
        CoreSession sessionAdmin = openSession(admin);
        route = sessionAdmin.getDocument(routeDocRef).getAdapter(
                DocumentRoute.class);
        assertTrue(route.isDone());
        Serializable v = route.getDocument().getPropertyValue(
                "fctroute1:globalVariable");
        assertEquals("myuser1", v);
        closeSession(sessionAdmin);
    }

    @SuppressWarnings("unchecked")
    @Test
    @Ignore
    public void testRestartWorkflowOperation() throws Exception {
        assertEquals("file", doc.getTitle());
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(node1,
                transition("trans12", "node2", "true", "testchain_title1"));
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        setTransitions(node2, transition("trans23", "node3", "true"));
        node2 = session.saveDocument(node2);

        DocumentModel node3 = createNode(routeDoc, "node3");
        node3.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node3 = session.saveDocument(node3);

        DocumentRoute route = instantiateAndRun();
        assertFalse(route.isDone());

        List<Task> tasks = taskService.getTaskInstances(doc,
                (NuxeoPrincipal) null, session);
        assertEquals(1, tasks.size());

        OperationContext ctx = new OperationContext(session);
        OperationChain chain = new OperationChain("testChain");
        chain.add(BulkRestartWorkflow.ID).set("workflowId", routeDoc.getTitle());
        automationService.run(ctx, chain);
        //process invalidations from automation context
        session.save();
        // query for all the workflows
        DocumentModelList workflows = session.query(String.format(
                "Select * from DocumentRoute where docri:participatingDocuments IN ('%s') and ecm:currentLifeCycleState = 'running'",
                doc.getId()));
        assertEquals(1, workflows.size());
        String restartedWorkflowId = workflows.get(0).getId();
        assertFalse(restartedWorkflowId.equals(route.getDocument().getId()));

        chain.add(BulkRestartWorkflow.ID).set("workflowId", routeDoc.getTitle()).set(
                "nodeId", "node2");
        automationService.run(ctx, chain);
        //process invalidations from automation context
        session.save();
        // query for all the workflows
        workflows = session.query(String.format(
                "Select * from DocumentRoute where docri:participatingDocuments IN ('%s') and ecm:currentLifeCycleState = 'running'",
                doc.getId()));
        assertEquals(1, workflows.size());
        assertFalse(restartedWorkflowId.equals(workflows.get(0).getId()));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMergeOneWhenHavinOpenedTasks() throws Exception {
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(node1, transition("trans12", "node2", "true"),
                transition("trans13", "node3", "true"));
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_title1");
        node2.setPropertyValue(GraphNode.PROP_HAS_TASK, "true");
        setTransitions(node2, transition("trans25", "node5", "true"));
        node2 = session.saveDocument(node2);

        DocumentModel node3 = createNode(routeDoc, "node3");
        node3.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_descr1");
        setTransitions(node3, transition("trans34", "node4", "true"));
        node3 = session.saveDocument(node3);

        DocumentModel node4 = createNode(routeDoc, "node4");
        node4.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_descr2");
        setTransitions(node4, transition("trans45", "node5", "true"));
        node4.setPropertyValue(GraphNode.PROP_HAS_TASK, Boolean.TRUE);
        NuxeoPrincipal user1 = userManager.getPrincipal("myuser1");
        node4.setPropertyValue(GraphNode.PROP_TASK_ASSIGNEES,
                new String[] { user1.getName() });
        node4 = session.saveDocument(node4);

        DocumentModel node5 = createNode(routeDoc, "node5");
        node5.setPropertyValue(GraphNode.PROP_MERGE, "one");
        node5.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_rights1");
        node5.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node5 = session.saveDocument(node5);

        DocumentRoute route = instantiateAndRun();
        session.save(); // process invalidations
        // verify that there are 2 open tasks
        List<Task> tasks = taskService.getAllTaskInstances(
                route.getDocument().getId(), session);
        assertNotNull(tasks);
        assertEquals(2, tasks.size());

        tasks = taskService.getAllTaskInstances(route.getDocument().getId(),
                user1, session);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        // process one of the tasks
        CoreSession session1 = openSession(user1);
        try {
            routing.endTask(session1, tasks.get(0),
                    new HashMap<String, Object>(), null);
        } finally {
            closeSession(session1);
        }

        // verify that route was done
        session.save(); // process invalidations
        route = session.getDocument(route.getDocument().getRef()).getAdapter(
                DocumentRoute.class);
        // verify that the merge one canceled the other tasks
        tasks = taskService.getAllTaskInstances(route.getDocument().getId(),
                session);
        assertNotNull(tasks);
        assertEquals(0, tasks.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testForceResumeOnMerge() throws Exception {
        DocumentModel node1 = createNode(routeDoc, "node1");
        node1.setPropertyValue(GraphNode.PROP_START, Boolean.TRUE);
        setTransitions(node1, transition("trans12", "node2", "true"),
                transition("trans13", "node3", "true"));
        node1 = session.saveDocument(node1);

        DocumentModel node2 = createNode(routeDoc, "node2");
        node2.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_title1");
        node2.setPropertyValue(GraphNode.PROP_HAS_TASK, "true");
        setTransitions(node2, transition("trans25", "node5", "true"));
        node2 = session.saveDocument(node2);

        DocumentModel node3 = createNode(routeDoc, "node3");
        node3.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_descr1");
        setTransitions(node3, transition("trans34", "node4", "true"));
        node3 = session.saveDocument(node3);

        DocumentModel node4 = createNode(routeDoc, "node4");
        node4.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_descr2");
        setTransitions(node4, transition("trans45", "node5", "true"));
        node4 = session.saveDocument(node4);

        DocumentModel node5 = createNode(routeDoc, "node5");
        node5.setPropertyValue(GraphNode.PROP_MERGE, "all");
        node5.setPropertyValue(GraphNode.PROP_INPUT_CHAIN, "testchain_rights1");
        node5.setPropertyValue(GraphNode.PROP_STOP, Boolean.TRUE);
        node5 = session.saveDocument(node5);

        DocumentRoute route = instantiateAndRun();
        // force resume on normal node, shouldn't change anything
        Map<String, Object> data = new HashMap<String, Object>();
        data.put(DocumentRoutingConstants.WORKFLOW_FORCE_RESUME, true);
        routing.resumeInstance(route.getDocument().getId(), "node2", data,
                null, session);
        session.save();
        assertEquals(
                "done",
                session.getDocument(route.getDocument().getRef()).getCurrentLifeCycleState());

        // force resume on merge on Waiting, but it shouldn't work
        // since the type of merge is all
        routeDoc = session.getDocument(routeDoc.getRef());
        route = instantiateAndRun();
        GraphRoute graph = (GraphRoute) route;
        GraphNode nodeMerge = graph.getNode("node5");
        assertTrue(State.WAITING.equals(nodeMerge.getState()));

        data = new HashMap<String, Object>();
        data.put(DocumentRoutingConstants.WORKFLOW_FORCE_RESUME, true);
        routing.resumeInstance(route.getDocument().getId(), "node5", data,
                null, session);
        session.save();

        // verify that the route is still running
        assertEquals(
                "running",
                session.getDocument(route.getDocument().getRef()).getCurrentLifeCycleState());

        // change merge type on the route instance and force resume again
        nodeMerge.getDocument().setPropertyValue(GraphNode.PROP_MERGE, "one");
        session.saveDocument(nodeMerge.getDocument());
        routing.resumeInstance(route.getDocument().getId(), "node5", data,
                null, session);
        session.save();

        assertEquals(
                "done",
                session.getDocument(route.getDocument().getRef()).getCurrentLifeCycleState());

    }
}