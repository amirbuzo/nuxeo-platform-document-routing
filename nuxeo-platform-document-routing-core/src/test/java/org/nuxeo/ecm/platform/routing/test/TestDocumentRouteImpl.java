/*
 * (C) Copyright 2009 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     arussel
 */
package org.nuxeo.ecm.platform.routing.test;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.nuxeo.ecm.platform.routing.api.DocumentRoute;

/**
 * @author arussel
 *
 */
public class TestDocumentRouteImpl extends DocumentRoutingTestCase {
    protected DocumentRoute routeModel;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        routeModel = createDocumentRouteModel(session, ROUTE1, ROOT_PATH).getAdapter(
                DocumentRoute.class);
    }

    @Test
    public void testMethods() {
        assertNotNull(routeModel);
        assertEquals(ROUTE1, routeModel.getName());
    }
}
