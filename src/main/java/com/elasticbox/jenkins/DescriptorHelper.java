/*
 * ElasticBox Confidential
 * Copyright (c) 2014 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins;

import com.elasticbox.Client;
import com.elasticbox.jenkins.util.ClientCache;
import com.elasticbox.jenkins.util.SlaveInstance;
import hudson.slaves.Cloud;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
/**
 *
 * @author Phong Nguyen Le
 */
public class DescriptorHelper {
    private static final Logger LOGGER = Logger.getLogger(DescriptorHelper.class.getName());
    
    public static final String ANY_BOX = "AnyBox";
    
    public static class JSONArrayResponse implements HttpResponse {
        private final JSONArray jsonArray;
        
        public JSONArrayResponse(JSONArray jsonArray) {
            this.jsonArray = jsonArray;
        }

        public JSONArray getJsonArray() {
            return jsonArray;
        }                

        public void generateResponse(StaplerRequest request, StaplerResponse response, Object node) throws IOException, ServletException {
            response.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            response.getWriter().write(jsonArray.toString());
        }
        
    }
    
    public static ListBoxModel getClouds() {
        ListBoxModel clouds = new ListBoxModel();
        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof ElasticBoxCloud) {
                clouds.add(cloud.getDisplayName(), cloud.name);
            }
        }
        
        return clouds;
    }
        
    public static ListBoxModel getWorkspaces(String cloud) {
        return getWorkspaces(ClientCache.getClient(cloud));
    }
    
    public static ListBoxModel getWorkspaces(Client client) {
        ListBoxModel workspaces = new ListBoxModel();
        if (client == null) {
            return workspaces;
        }
        
        try {
            for (Object workspace : client.getWorkspaces()) {
                JSONObject json = (JSONObject) workspace;
                workspaces.add(json.getString("name"), json.getString("id"));
            }                    
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error fetching workspaces", ex);
        }

        return sort(workspaces);        
    }

    public static ListBoxModel getBoxes(Client client, String workspace) {
        ListBoxModel boxes = new ListBoxModel();
        if (StringUtils.isBlank(workspace) || client == null) {
            return boxes;
        }

        try {
            for (Object box : client.getBoxes(workspace)) {
                JSONObject json = (JSONObject) box;
                boxes.add(json.getString("name"), json.getString("id"));
            }                    
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error fetching boxes", ex);
        }                

        return sort(boxes);        
    }

    public static ListBoxModel getBoxes(String cloud, String workspace) {
        return getBoxes(ClientCache.getClient(cloud), workspace);
    }
    
    public static ListBoxModel getBoxVersions(Client client, String box) {
        ListBoxModel boxVersions = new ListBoxModel();
        if (StringUtils.isBlank(box) || client == null) {
            return boxVersions;
        }
        
        boxVersions.add("Latest", box);
        try {
            for (Object json : client.getBoxVersions(box)) {
                JSONObject boxVersion = (JSONObject) json;
                boxVersions.add(boxVersion.getJSONObject("version").getString("description"), boxVersion.getString("id"));
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error fetching box versions", ex);
        }
        
        return boxVersions;
    }
    
    public static ListBoxModel getBoxVersions(String cloud, String box) {
        return getBoxVersions(ClientCache.getClient(cloud), box);
    }

    public static ListBoxModel getProfiles(Client client, String workspace, String box) {
        ListBoxModel profiles = new ListBoxModel();
        if (StringUtils.isBlank(workspace) || StringUtils.isBlank(box) || client == null) {
            return profiles;
        }

        try {
            for (Object profile : client.getProfiles(workspace, box)) {
                JSONObject json = (JSONObject) profile;
                profiles.add(json.getString("name"), json.getString("id"));
            }                    
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error fetching profiles", ex);
        }

        return sort(profiles);        
    }
    
    public static ListBoxModel getProfiles(String cloud, String workspace, String box) {
        return getProfiles(ClientCache.getClient(cloud), workspace, box);
    }
    
    public static JSONArrayResponse getBoxStack(Client client, String boxId) {
        if (client != null && StringUtils.isNotBlank(boxId)) {
            try {
                return new JSONArrayResponse(new BoxStack(boxId, client.getBoxStack(boxId), client).toJSONArray());
                
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching variables for box {0}", boxId), ex);
            }
        }
        
        return new JSONArrayResponse(new JSONArray());        
    }
    
    public static JSONArrayResponse getBoxStack(String cloud, String boxId) {
        return getBoxStack(ClientCache.getClient(cloud), boxId);
    }
    
    public static JSONArrayResponse getInstanceBoxStack(Client client, String instance) {
        if (client != null && StringUtils.isNotBlank(instance)) {
            try {
                JSONObject instanceJson = client.getInstance(instance);
                JSONArray boxes = instanceJson.getJSONArray("boxes");
                return new JSONArrayResponse(new BoxStack(boxes.getJSONObject(0).getString("id"), boxes, client).toJSONArray());
                
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching variables for profile {0}", instance), ex);
            }
        }
        
        return new JSONArrayResponse(new JSONArray());
    }
    
    public static JSONArrayResponse getInstanceBoxStack(String cloud, String instance) {
        return getInstanceBoxStack(ClientCache.getClient(cloud), instance);
    }

    /**
     * Returns only the variables of main box for now.
     * 
     * @param cloud
     * @param instance
     * @return 
     */
    public static JSONArrayResponse getInstanceVariables(String cloud, String instance) {
        Client client = ClientCache.getClient(cloud);
        if (client != null && StringUtils.isNotBlank(instance)) {
            try {
                JSONObject json = client.getInstance(instance);
                JSONArray variables = json.getJSONArray("boxes").getJSONObject(0).getJSONArray("variables");
                for (Object modifiedVariable : json.getJSONArray("variables")) {
                    JSONObject modifiedVariableJson = (JSONObject) modifiedVariable;
                    for (Object variable : variables) {
                        JSONObject variableJson = (JSONObject) variable;
                        if (variableJson.getString("name").equals(modifiedVariableJson.getString("name"))) {
                            variableJson.put("value", modifiedVariableJson.getString("value"));
                            break;
                        }
                    }
                 }
                return new JSONArrayResponse(variables);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching variables for instance {0}", instance), ex);
            }
        }
        
        return new JSONArrayResponse(new JSONArray());        
    }
    
    private static class InstanceFilter {
        private final String boxId;

        InstanceFilter(String boxId) {
            this.boxId = boxId;
        }
        
        public boolean accept(JSONObject instance) {
            if (Client.TERMINATE_OPERATIONS.contains(instance.getString("operation"))) {
                return false;
            }
            
            if (boxId == null || boxId.isEmpty() || boxId.equals(ANY_BOX)) {
                return true;
            }
            
            return new BoxStack(boxId, instance.getJSONArray("boxes"), null).findBox(boxId) != null;
        }
    }
    
    public static JSONArrayResponse getInstancesAsJSONArrayResponse(Client client, String workspace, String box) {
        JSONArray instances = new JSONArray();
        if (client == null || StringUtils.isBlank(workspace) || StringUtils.isBlank(box)) {
            return new JSONArrayResponse(instances);
        }

        try {
            JSONArray instanceArray = client.getInstances(workspace);
            if (!instanceArray.isEmpty() && !instanceArray.getJSONObject(0).getJSONArray("boxes").getJSONObject(0).containsKey("id")) {
                List<String> instanceIDs = new ArrayList<String>();
                for (int i = 0; i < instanceArray.size(); i++) {
                    instanceIDs.add(instanceArray.getJSONObject(i).getString("id"));
                }
                instanceArray = client.getInstances(workspace, instanceIDs);
            }
            InstanceFilter instanceFilter = new InstanceFilter(box);
            for (Object instance : instanceArray) {
                JSONObject json = (JSONObject) instance;
                if (instanceFilter.accept(json)) {
                    json.put("name", MessageFormat.format("{0} - {1} - {2}", json.getString("name"), 
                            json.getString("environment"), json.getJSONObject("service").getString("id")));
                    instances.add(json);
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Error fetching instances of workspace {0}", workspace), ex);
        }
        
        Collections.sort(instances, new Comparator<Object> () {
            public int compare(Object o1, Object o2) {
                return ((JSONObject) o1).getString("name").compareTo(((JSONObject) o2).getString("name"));
            }
        });
        
        return new JSONArrayResponse(instances);
    }
    
    public static JSONArrayResponse getInstancesAsJSONArrayResponse(String cloud, String workspace, String box) {
        return getInstancesAsJSONArrayResponse(ClientCache.getClient(cloud), workspace, box);
    }
    
    public static ListBoxModel getInstances(Client client, String workspace, String box) {
        ListBoxModel instances = new ListBoxModel();
        JSONArray instanceArray = getInstancesAsJSONArrayResponse(client, workspace, box).getJsonArray();
        for (Object instance : instanceArray) {
            JSONObject json = (JSONObject) instance;
            instances.add(json.getString("name"), json.getString("id"));
        }
        return instances;
    }
    
    public static ListBoxModel getInstances(String cloud, String workspace, String box) {
        return getInstances(ClientCache.getClient(cloud), workspace, box);
    }
    
    public static FormValidation checkSlaveBox(Client client, String box) {
        JSONArray stack = getBoxStack(client, box).getJsonArray();
        if (stack.isEmpty()) {
            return FormValidation.ok();
        }
        
        String variableListStr = StringUtils.join(SlaveInstance.REQUIRED_VARIABLES, ", ");
        if (SlaveInstance.isSlaveBox(stack.getJSONObject(0))) {
            return FormValidation.ok();
        } else if (stack.size() == 1) {
            return FormValidation.error(MessageFormat.format("The selected box version does not have the following required variables: {0}", variableListStr));
        }

        JSONObject slaveBox = null;
        for (int i = 1; i < stack.size(); i++) {
            JSONObject stackBox = stack.getJSONObject(i);
            if (SlaveInstance.isSlaveBox(stackBox)) {
                slaveBox = stackBox;
                break;
            }
        }

        if (slaveBox != null) {
            return FormValidation.ok(MessageFormat.format("The required variables {0} are detected in child box {1}. They will be set by Jenkins at deployment time.", variableListStr, slaveBox.getString("name")));
        } else {
            String message = MessageFormat.format("The selected box version and its child boxes do not have the following required variables: {0}", variableListStr);
            return FormValidation.error(message);
        }        
    }
    
    public static FormValidation checkCloud(String cloud) {
        if (StringUtils.isBlank(cloud)) {
            return FormValidation.error("Cloud is required");
        }
        
        try {
            ClientCache.findOrCreateClient(cloud);
            return FormValidation.ok();
        } catch (IOException ex) {
            return FormValidation.error(ex.getMessage() != null ? ex.getMessage() : "Cannot connect to the cloud");
        }
    }
    
    private static ListBoxModel sort(ListBoxModel model) {
        Collections.sort(model, new Comparator<ListBoxModel.Option> () {
            public int compare(ListBoxModel.Option o1, ListBoxModel.Option o2) {
                return o1.name.compareTo(o2.name);
            }
        });
        return model;
    }

    private static class BoxStack {
        private final List<JSONObject> overriddenVariables;
        private final JSONArray boxes;
        private final String boxId;
        private final Client client;
        
        BoxStack(String boxId, JSONArray boxes, Client client) {
            this.boxId = boxId;
            this.boxes = boxes;      
            this.client = client;
            overriddenVariables = new ArrayList<JSONObject>();
        }
        
        public JSONArray toJSONArray() {
            return JSONArray.fromObject(createBoxStack("", boxId));
        }
        
        private JSONObject findBox(String boxId) {
            for (Object json : boxes) {
                JSONObject box = (JSONObject) json;
                if (box.getString("id").equals(boxId)) {
                    return box;
                }
            }
            
            for (Object json : boxes) {
                JSONObject box = (JSONObject) json;
                if (box.containsKey("version") && box.getJSONObject("version").getString("box").equals(boxId)) {
                    return box;
                }
            }      
            
            return null;
        }
        
        private JSONObject findOverriddenVariable(String name, String scope) {
            for (Object json : overriddenVariables) {
                JSONObject variable = (JSONObject) json;
                if (scope.equals(variable.get("scope")) && variable.get("name").equals(name)) {
                    return variable;
                }
            }

            return null;
        }

        private List<JSONObject> createBoxStack(String scope, String boxId) {
            JSONObject box = findBox(boxId);
            if (box == null) {
                return Collections.EMPTY_LIST;
            }
            
            List<JSONObject> boxStack = new ArrayList<JSONObject>();
            JSONObject stackBox = new JSONObject();
            String icon = null;
            if (box.containsKey("icon")) {
                icon = box.getString("icon");
            }
            if (icon == null || icon.isEmpty()) {
                icon = "/images/platform/box.png";
            } else if (icon.charAt(0) != '/') {
                icon = '/' + icon;
            }
            stackBox.put("id", box.getString("id"));
            stackBox.put("name", box.getString("name"));
            stackBox.put("icon", client.getEndpointUrl() + icon);
            boxStack.add(stackBox);
            JSONArray stackBoxVariables = new JSONArray();
            JSONArray variables = box.getJSONArray("variables");
            List<JSONObject> boxVariables = new ArrayList<JSONObject>();
            for (Object json : variables) {
                JSONObject variable = (JSONObject) json;
                String varScope = (String) variable.get("scope");
                if (varScope != null && !varScope.isEmpty()) {
                    String fullScope = scope.isEmpty() ? varScope : scope + '.' + varScope;
                    if (findOverriddenVariable(variable.getString("name"), scope) == null) {
                        JSONObject overriddenVariable = JSONObject.fromObject(variable);
                        overriddenVariable.put("scope", fullScope);
                        overriddenVariables.add(variable);
                    }
                } else if (variable.getString("type").equals("Box")) {
                    boxVariables.add(variable);
                } else {
                    JSONObject stackBoxVariable = JSONObject.fromObject(variable);
                    stackBoxVariable.put("scope", scope);
                    JSONObject overriddenVariable = findOverriddenVariable(stackBoxVariable.getString("name"), scope);
                    if (overriddenVariable != null) {
                        stackBoxVariable.put("value", overriddenVariable.get("value"));
                    }
                    stackBoxVariables.add(stackBoxVariable);
                }
            }        
            stackBox.put("variables", stackBoxVariables);

            for (JSONObject boxVariable : boxVariables) {
                String variableName = boxVariable.getString("name");
                boxStack.addAll(createBoxStack(scope.isEmpty() ? variableName : scope + '.' + variableName, boxVariable.getString("value")));
            }

            return boxStack;
        }
            
    }
        
}
