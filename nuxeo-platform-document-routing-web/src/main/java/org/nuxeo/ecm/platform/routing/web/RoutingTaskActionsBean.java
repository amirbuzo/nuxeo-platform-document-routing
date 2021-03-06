/*
 * (C) Copyright 2012 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *    Mariana Cedica
 *
 * $Id$
 */
package org.nuxeo.ecm.platform.routing.web;

import static org.jboss.seam.ScopeType.CONVERSATION;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.faces.application.FacesMessage;
import javax.faces.component.EditableValueHolder;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.validator.ValidatorException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Observer;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.intercept.BypassInterceptors;
import org.jboss.seam.annotations.web.RequestParameter;
import org.jboss.seam.core.Events;
import org.jboss.seam.faces.FacesMessages;
import org.jboss.seam.international.StatusMessage;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.actions.Action;
import org.nuxeo.ecm.platform.actions.ejb.ActionManager;
import org.nuxeo.ecm.platform.contentview.seam.ContentViewActions;
import org.nuxeo.ecm.platform.forms.layout.api.LayoutDefinition;
import org.nuxeo.ecm.platform.forms.layout.service.WebLayoutManager;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.ecm.platform.routing.api.exception.DocumentRouteException;
import org.nuxeo.ecm.platform.routing.core.impl.GraphNode;
import org.nuxeo.ecm.platform.routing.core.impl.GraphNode.Button;
import org.nuxeo.ecm.platform.routing.core.impl.GraphRoute;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskEventNames;
import org.nuxeo.ecm.platform.task.TaskImpl;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.ecm.platform.ui.web.util.ComponentUtils;
import org.nuxeo.ecm.webapp.action.ActionContextProvider;
import org.nuxeo.ecm.webapp.documentsLists.DocumentsListsManager;
import org.nuxeo.ecm.webapp.helpers.EventNames;
import org.nuxeo.runtime.api.Framework;

/**
 * Task validators
 *
 * @since 5.6
 */
@Scope(CONVERSATION)
@Name("routingTaskActions")
public class RoutingTaskActionsBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(RoutingTaskActionsBean.class);

    public static final String SUBJECT_PATTERN = "([a-zA-Z_0-9]*(:)[a-zA-Z_0-9]*)";

    /**
     * Runtime property name, that makes it possible to cache actions available
     * on a given task, depending on its type.
     * <p>
     * This caching is global to all tasks in the platform, and will not work
     * correctly if some tasks are filtering some actions depending on local
     * variables, for instance.
     *
     * @since 5.7
     */
    public static final String CACHE_ACTIONS_PER_TASK_TYPE_PROP_NAME = "org.nuxeo.routing.cacheActionsPerTaskType";

    @In(create = true, required = false)
    protected transient CoreSession documentManager;

    @In(required = true, create = true)
    protected NavigationContext navigationContext;

    @In(create = true, required = false)
    protected FacesMessages facesMessages;

    @In(create = true)
    protected Map<String, String> messages;

    @In(create = true)
    protected transient DocumentsListsManager documentsListsManager;

    @In(create = true, required = false)
    protected transient ActionContextProvider actionContextProvider;

    @In(create = true, required = false)
    protected ContentViewActions contentViewActions;

    protected Task currentTask;

    protected Map<String, Serializable> formVariables;

    @RequestParameter("button")
    protected String button;

    protected ActionManager actionService;

    public void validateTaskDueDate(FacesContext context,
            UIComponent component, Object value) {
        final String DATE_FORMAT = "dd/MM/yyyy";
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);

        String messageString = null;
        if (value != null) {
            Date today = null;
            Date dueDate = null;
            try {
                dueDate = dateFormat.parse(dateFormat.format((Date) value));
                today = dateFormat.parse(dateFormat.format(new Date()));
            } catch (ParseException e) {
                messageString = "label.workflow.error.date_parsing";
            }
            if (dueDate.before(today)) {
                messageString = "label.workflow.error.outdated_duedate";
            }
        }

        if (messageString != null) {
            FacesMessage message = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, ComponentUtils.translate(
                            context, "label.workflow.error.outdated_duedate"),
                    null);
            ((EditableValueHolder) component).setValid(false);
            context.addMessage(component.getClientId(context), message);
        }
    }

    public void validateSubject(FacesContext context, UIComponent component,
            Object value) {
        if (!((value instanceof String) && ((String) value).matches(SUBJECT_PATTERN))) {
            FacesMessage message = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, ComponentUtils.translate(
                            context, "label.document.routing.invalid.subject"),
                    null);
            context.addMessage(null, message);
            throw new ValidatorException(message);
        }
    }

    public String getTaskLayout(Task task) throws ClientException {
        return getTaskInfo(task, false).layout;
    }

    public List<Action> getTaskButtons(Task task) throws ClientException {
        List<Button> buttons = getTaskInfo(task, false).buttons;
        List<Action> actions = new ArrayList<Action>();
        for (Button button : buttons) {
            Action action = new Action(button.getName(),
                    Action.EMPTY_CATEGORIES);
            action.setLabel(button.getLabel());
            boolean displayAction = true;
            if (StringUtils.isNotEmpty(button.getFilter())) {
                displayAction = getActionService().checkFilter(button.filter,
                        actionContextProvider.createActionContext());
            }
            if (displayAction) {
                actions.add(action);
            }
        }
        return actions;
    }

    public String endTask(Task task) throws ClientException {
        // collect form data
        Map<String, Object> data = new HashMap<String, Object>();
        if (formVariables != null) {
            data.put("WorkflowVariables", formVariables);
            data.put("NodeVariables", formVariables);
            // if there is a comment on the submitted form, pass it to be
            // logged
            // by audit
            if (formVariables.containsKey("comment")) {
                data.put("comment", formVariables.get("comment"));
            }
        }
        // add the button name that was clicked
        try {
            DocumentRoutingService routing = Framework.getLocalService(DocumentRoutingService.class);
            routing.endTask(documentManager, task, data, button);
            facesMessages.add(StatusMessage.Severity.INFO,
                    messages.get("workflow.feedback.info.taskEnded"));
        } catch (DocumentRouteException e) {
            if (log.isDebugEnabled()) {
                log.debug(e, e);
            }
            facesMessages.add(StatusMessage.Severity.ERROR,
                    messages.get("workflow.feedback.error.taskEnded"));
        }
        Events.instance().raiseEvent(TaskEventNames.WORKFLOW_TASK_COMPLETED);
        clear();
        if (documentManager.hasPermission(
                navigationContext.getCurrentDocument().getRef(),
                SecurityConstants.READ)) {
            return null;
        }
        // if the user only had temporary permissions on the current doc given
        // by the workflow
        return navigationContext.goHome();
    }

    private void clear() {
        currentTask = null;
        formVariables = null;
        button = null;
    }

    public Task setCurrentTask(String taskDocId) throws ClientException {
        Task task = new TaskImpl(documentManager.getDocument(new IdRef(
                taskDocId)));
        return setCurrentTask(task);
    }

    public Task setCurrentTask(Task task) throws ClientException {
        currentTask = task;
        // clear form variables and button
        formVariables = null;
        button = null;
        return currentTask;
    }

    public Map<String, Serializable> getFormVariables(Task task)
            throws ClientException {
        return getTaskInfo(task, true).formVariables;
    }

    protected class TaskInfo {
        protected HashMap<String, Serializable> formVariables;

        protected String layout;

        protected List<Button> buttons;

        protected TaskInfo(HashMap<String, Serializable> formVariables,
                String layout, List<Button> buttons) {
            this.formVariables = formVariables;
            this.layout = layout;
            this.buttons = buttons;
        }
    }

    // we have to be unrestricted to get this info
    // because the current user may not be the one that started the
    // workflow
    protected TaskInfo getTaskInfo(Task task, final boolean getFormVariables)
            throws ClientException {
        final String routeDocId = task.getVariable(DocumentRoutingConstants.TASK_ROUTE_INSTANCE_DOCUMENT_ID_KEY);
        final String nodeId = task.getVariable(DocumentRoutingConstants.TASK_NODE_ID_KEY);
        if (routeDocId == null) {
            throw new ClientException(
                    "Can not get the source graph for this task");
        }
        if (nodeId == null) {
            throw new ClientException(
                    "Can not get the source node for this task");
        }
        final TaskInfo[] res = new TaskInfo[1];
        new UnrestrictedSessionRunner(documentManager) {
            @Override
            public void run() throws ClientException {
                DocumentModel doc = session.getDocument(new IdRef(routeDocId));
                GraphRoute route = doc.getAdapter(GraphRoute.class);
                GraphNode node = route.getNode(nodeId);
                HashMap<String, Serializable> map = new HashMap<String, Serializable>();
                if (getFormVariables) {
                    map.putAll(node.getVariables());
                    map.putAll(route.getVariables());
                }
                res[0] = new TaskInfo(map, node.getTaskLayout(),
                        node.getTaskButtons());
            }
        }.runUnrestricted();
        return res[0];
    }

    public Map<String, Serializable> getFormVariables() throws ClientException {
        if (formVariables == null) {
            if (currentTask == null) {
                throw new ClientException("No current task defined");
            }
            formVariables = getFormVariables(currentTask);
        }
        return formVariables;
    }

    public void setFormVariables(Map<String, Serializable> formVariables) {
        this.formVariables = formVariables;
    }

    /**
     * @since 5.6
     */
    public boolean isRoutingTask(Task task) {
        return task.getDocument().hasFacet(
                DocumentRoutingConstants.ROUTING_TASK_FACET_NAME);
    }

    /**
     * @since 5.6
     */
    public List<Action> getTaskActions(Task task) throws ClientException {
        return new ArrayList<Action>(getTaskActionsMap(task).values());
    }

    // temp method because Studio also refers to empty layouts
    protected boolean isLayoutEmpty(String layoutName) {
        if (layoutName == null || layoutName.isEmpty()) {
            return true;
        }
        try {
            // explore the layout and find out if it contains only empty
            // widgets
            WebLayoutManager lm = Framework.getService(WebLayoutManager.class);
            LayoutDefinition layout = lm.getLayoutDefinition(layoutName);
            if (layout == null || layout.isEmpty()) {
                return true;
            }
        } catch (Exception e) {
            log.error(e, e);
            return true;
        }

        return false;
    }

    /**
     * Helper to generate a unique action id for all task types
     *
     * @since 5.7
     */
    protected String getTaskActionId(Task task, String buttonId)
            throws ClientException {
        return String.format("%s_%s", task.getType(), buttonId);
    }

    /**
     * @since 5.6
     */
    public Map<String, Action> getTaskActionsMap(Task task)
            throws ClientException {
        Map<String, Action> actions = new LinkedHashMap<String, Action>();

        TaskInfo taskInfo = getTaskInfo(task, true);
        String layout = taskInfo.layout;
        List<Button> buttons = taskInfo.buttons;

        boolean addLayout = !isLayoutEmpty(layout);
        Map<String, Serializable> props = null;
        if (addLayout) {
            props = new HashMap<String, Serializable>();
            props.put("layout", layout);
            props.put("formVariables", taskInfo.formVariables);
        }

        if (buttons != null && !buttons.isEmpty()) {
            for (Button button : buttons) {
                String buttonId = button.getName();
                String id = getTaskActionId(task, buttonId);
                Action action = new Action(id, Action.EMPTY_CATEGORIES);
                action.setLabel(button.getLabel());
                Map<String, Serializable> actionProps = new HashMap<String, Serializable>();
                actionProps.put("buttonId", buttonId);
                if (addLayout) {
                    actionProps.putAll(props);
                    action.setProperties(actionProps);
                    action.setType("fancybox");
                } else {
                    action.setProperties(actionProps);
                    action.setType("link");
                }
                boolean displayAction = true;
                if (StringUtils.isNotEmpty(button.getFilter())) {
                    displayAction = getActionService().checkFilter(
                            button.filter,
                            actionContextProvider.createActionContext());
                }
                if (displayAction) {
                    actions.put(id, action);
                }
            }
        }

        // If there is a form attached to these tasks, add a generic
        // process action to open the fancy box.
        // The form of the first task will be displayed, but all the tasks
        // concerned by this action share the same form, as they share the
        // same type.
        if (addLayout && !actions.isEmpty()) {
            String id = getTaskActionId(task, "process_task");
            Action processAction = new Action(id, Action.EMPTY_CATEGORIES);
            processAction.setProperties(props);
            processAction.setType("process_task");
            actions.put(id, processAction);
        }

        return actions;
    }

    /**
     * Returns actions for task document buttons defined in the workflow graph
     *
     * @since 5.6
     */
    @SuppressWarnings("boxing")
    public List<Action> getTaskActions(String selectionListName)
            throws ClientException {
        Map<String, Action> actions = new LinkedHashMap<String, Action>();
        Map<String, Map<String, Action>> actionsPerTaskType = new LinkedHashMap<String, Map<String, Action>>();
        Map<String, Integer> actionsCounter = new HashMap<String, Integer>();
        List<DocumentModel> docs = documentsListsManager.getWorkingList(selectionListName);
        boolean cachePerType = Boolean.TRUE.equals(Boolean.valueOf(Framework.getProperty(CACHE_ACTIONS_PER_TASK_TYPE_PROP_NAME)));
        int taskDocsNum = 0;
        if (docs != null && !docs.isEmpty()) {
            for (DocumentModel doc : docs) {
                if (doc.hasFacet(DocumentRoutingConstants.ROUTING_TASK_FACET_NAME)) {
                    Task task = new TaskImpl(doc);
                    String taskType = task.getType();
                    Map<String, Action> taskActions = Collections.emptyMap();
                    // if caching per type, fill the per type map, else update
                    // actions directly
                    if (cachePerType) {
                        if (actionsPerTaskType.containsKey(taskType)) {
                            taskActions = actionsPerTaskType.get(taskType);
                        } else {
                            taskActions = getTaskActionsMap(task);
                            actionsPerTaskType.put(taskType, taskActions);
                        }
                    } else {
                        taskActions = getTaskActionsMap(task);
                        actions.putAll(taskActions);
                    }
                    for (String actionId : taskActions.keySet()) {
                        Integer count = actionsCounter.get(actionId);
                        if (count == null) {
                            actionsCounter.put(actionId, 1);
                        } else {
                            actionsCounter.put(actionId, count + 1);
                        }
                    }
                    taskDocsNum++;
                }
            }
        }
        if (cachePerType) {
            // initialize actions for cache map
            for (Map<String, Action> actionsPerType : actionsPerTaskType.values()) {
                actions.putAll(actionsPerType);
            }
        }
        List<Action> res = new ArrayList<Action>(actions.values());
        for (Action action : res) {
            if (!actionsCounter.get(action.getId()).equals(taskDocsNum)) {
                action.setAvailable(false);
            }
        }
        return res;
    }

    /**
     * Ends a task given a selection list name and an action
     *
     * @since 5.6
     */
    @SuppressWarnings("unchecked")
    public String endTasks(String selectionListName, Action taskAction)
            throws ClientException {
        // collect form data
        Map<String, Object> data = new HashMap<String, Object>();
        String buttonId = (String) taskAction.getProperties().get("buttonId");
        Map<String, Serializable> formVariables = (Map<String, Serializable>) taskAction.getProperties().get(
                "formVariables");
        if (formVariables != null) {
            data.put("WorkflowVariables", formVariables);
            data.put("NodeVariables", formVariables);
            // if there is a comment on the submitted form, pass it to be
            // logged by audit
            if (formVariables.containsKey("comment")) {
                data.put("comment", formVariables.get("comment"));
            }
        }

        // get task documents
        boolean hasErrors = false;
        DocumentRoutingService routing = Framework.getLocalService(DocumentRoutingService.class);
        List<DocumentModel> docs = documentsListsManager.getWorkingList(selectionListName);
        if (docs != null && !docs.isEmpty()) {
            for (DocumentModel doc : docs) {
                if (doc.hasFacet(DocumentRoutingConstants.ROUTING_TASK_FACET_NAME)) {
                    // add the button name that was clicked
                    try {
                        routing.endTask(documentManager, new TaskImpl(doc),
                                data, buttonId);
                    } catch (DocumentRouteException e) {
                        log.error(e, e);
                        hasErrors = true;
                    }
                }
            }
        }
        if (hasErrors) {
            facesMessages.add(StatusMessage.Severity.ERROR,
                    messages.get("workflow.feedback.error.tasksEnded"));
        } else {
            facesMessages.add(StatusMessage.Severity.INFO,
                    messages.get("workflow.feedback.info.tasksEnded"));
        }
        // reset selection list
        documentsListsManager.resetWorkingList(selectionListName);
        // raise document change event to trigger refresh of content views
        // listing task documents.
        Events.instance().raiseEvent(EventNames.DOCUMENT_CHANGED);
        Events.instance().raiseEvent(TaskEventNames.WORKFLOW_TASK_COMPLETED);
        return null;
    }

    private ActionManager getActionService() {
        if (actionService == null) {
            actionService = Framework.getLocalService(ActionManager.class);
        }
        return actionService;
    }

    /***
     * @since 5.7
     */
    @Observer(value = { TaskEventNames.WORKFLOW_TASK_COMPLETED })
    @BypassInterceptors
    public void OnTaskCompleted() {
        if (contentViewActions != null) {
            contentViewActions.refreshOnSeamEvent(TaskEventNames.WORKFLOW_TASK_COMPLETED);
            contentViewActions.resetPageProviderOnSeamEvent(TaskEventNames.WORKFLOW_TASK_COMPLETED);
        }
    }
}
