/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     ldoguin
 */
package org.nuxeo.ecm.platform.routing.dm.test;

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.storage.sql.SQLRepositoryTestCase;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.ecm.platform.routing.dm.adapter.RoutingTask;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskService;
import org.nuxeo.ecm.platform.task.test.TaskUTConstants;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

/**
 * @author ldoguin
 *
 */
public class TestRoutingTaskService extends SQLRepositoryTestCase {

    protected UserManager userManager;

    protected NuxeoPrincipal administrator;

    protected NuxeoPrincipal user1;

    protected NuxeoPrincipal user2;

    protected NuxeoPrincipal user3;

    protected NuxeoPrincipal user4;

    protected DocumentModel targetDoc;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        deployBundle("org.nuxeo.ecm.platform.content.template");
        deployBundle("org.nuxeo.ecm.directory");
        deployBundle("org.nuxeo.ecm.platform.usermanager");
        deployBundle("org.nuxeo.ecm.directory.types.contrib");
        deployBundle("org.nuxeo.ecm.directory.sql");
        deployBundle("org.nuxeo.ecm.platform.task.core");
        deployBundle("org.nuxeo.ecm.platform.routing.core");
        deployBundle(TaskUTConstants.CORE_BUNDLE_NAME);
        deployBundle(TaskUTConstants.TESTING_BUNDLE_NAME);


        deployBundle(TestConstants.DM_BUNDLE);

        userManager = Framework.getService(UserManager.class);
        assertNotNull(userManager);

        administrator = userManager.getPrincipal(SecurityConstants.ADMINISTRATOR);
        assertNotNull(administrator);

        user1 = userManager.getPrincipal("myuser1");
        assertNotNull(user1);

        user2 = userManager.getPrincipal("myuser2");
        assertNotNull(user2);

        user3 = userManager.getPrincipal("myuser3");
        assertNotNull(user3);

        user4 = userManager.getPrincipal("myuser4");
        assertNotNull(user4);


        openSession();
        targetDoc = session.createDocumentModel("/", "targetDocument", "File");
        targetDoc = session.createDocument(targetDoc);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testService() throws Exception {
        TaskService taskService = Framework.getLocalService(TaskService.class);
        DocumentRoutingService routing = Framework.getLocalService(DocumentRoutingService.class);
        List<String> actorIds = new ArrayList<String>();
        List<Task> tasks = taskService.createTask(session, administrator,
                targetDoc, "MyRoutingTask", actorIds, false, null, null, null,
                null, "/");
        routing.makeRoutingTasks(session, tasks);
        session.save();
        DocumentModel taskDoc = session.getDocument(new PathRef("/MyRoutingTask"));
        RoutingTask routingTask = taskDoc.getAdapter(RoutingTask.class);
        assertNotNull(routingTask);
        closeSession(session);
    }

    @Test
    public void testTaskStep() throws Exception {
        DocumentModel taskStep = session.createDocumentModel("/","simpleTask","SimpleTask");
        assertNotNull(taskStep);
        taskStep = session.createDocument(taskStep);
        assertNotNull(taskStep);
        closeSession(session);
    }
}
