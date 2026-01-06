import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.*;

import java.io.IOException;
import java.util.*;

public class PortEolPackagesUpdater {
    private static final String PORT_BASE_URL = "https://api.getport.io/v1";
//    private static final String PORT_CLIENT_ID = "RFbtPBJzvF1YGPBjHxIsIl00EidJkOsD";
//    private static final String PORT_CLIENT_SECRET = "3An8D3XE2wYtGVAXz3f3231YIOZN4DLGX4jOiI7hSFsukh7EeIVYMeKoMaqsAWSw";
    private static final String PORT_CLIENT_ID = "PvzxL7xoHjYcmbubcDj18vTNV3nreaNG";
    private static final String PORT_CLIENT_SECRET = "mqtFNChaVDwneKygEZAqHN00fYbQ7C5LFWSMDScKyQeA2xnTKSb6X0DQ3ScPY3NC";
    
    private static final String SERVICE_BLUEPRINT = "service";
    private static final String FRAMEWORK_BLUEPRINT = "framework";
    private static final String RELATION_NAME = "used_frameworks";
    private static final String EOL_PROPERTY = "state";
    private static final String EOL_VALUE = "EOL";
    private static final String TARGET_PROPERTY = "number_of_eol_packages";

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private String accessToken;

    public static void main(String[] args) {
        PortEolPackagesUpdater updater = new PortEolPackagesUpdater();
        
        try {
            updater.run();
        } 
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

	private void run() throws IOException {
        accessToken = getPortToken();
        
        System.out.println("Access Token: " + accessToken);
        
        List<Map<String, Object>> services = listEntities(SERVICE_BLUEPRINT);
        
        System.out.println("Service Entities List: " + services.toString());
        
        for (Map<String, Object> service : services) {
            String serviceId = (String) service.get("identifier");
            
            System.out.println("ServiceId: " + serviceId);
            
            List<String> frameworkIds = getRelationIdentifiers(service, RELATION_NAME);
            
            System.out.println("Frameworks associated to the Service: " + frameworkIds.toString());
            
            int eolCount = 0;
            
            for (String frameworkId : frameworkIds) {
            	System.out.println("Getting Entity Id: " + frameworkId);
            	
            	Map<String, Object> framework = getEntity(FRAMEWORK_BLUEPRINT, frameworkId);
            	            	
            	ArrayList state = (ArrayList) (((Map) ((Map) framework.get("entity")).get("properties")).get(EOL_PROPERTY));
                
            	System.out.println("a: " + state.get(0));
            	
                if (EOL_VALUE.equals(state.get(0).toString())) {
                    eolCount++;
                }
            }
            System.out.println("Number of EOL Entities: " + eolCount);
            
            updateServiceEolCount(serviceId, eolCount);
            
            System.out.printf("Updated service %s with %d EOL packages%n", serviceId, eolCount);
        }
    }

    private String getPortToken() throws IOException {
    	System.out.println("Obtaining Access Token");
    	
        String url = PORT_BASE_URL + "/auth/access_token";
        
        ObjectNode payload = mapper.createObjectNode().put("clientId", PORT_CLIENT_ID).put("clientSecret", PORT_CLIENT_SECRET);

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json"));

        Request request = new Request.Builder().url(url).post(body).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
            	throw new IOException("Auth failed: " + response);
            }
            ObjectNode json = (ObjectNode) mapper.readTree(response.body().string());
            
            return json.get("accessToken").asText();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listEntities(String blueprint) throws IOException {
    	System.out.println("Obtaining Service Entities List");
    	
        List<Map<String, Object>> entities = new ArrayList<>();
        String cursor = null;

        do {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(PORT_BASE_URL + "/blueprints/" + blueprint + "/entities").newBuilder();
            if (cursor != null) {
                urlBuilder.addQueryParameter("cursor", cursor);
            }

            Request request = new Request.Builder().url(urlBuilder.build()).addHeader("Authorization", "Bearer " + accessToken).build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                	throw new IOException("Failed to list entities: " + response);
                }
                ObjectNode json = (ObjectNode) mapper.readTree(response.body().string());
                
                ArrayNode entitiesArray = (ArrayNode) json.get("entities");
                
                for (int i = 0; i < entitiesArray.size(); i++) {
                    entities.add(mapper.convertValue(entitiesArray.get(i), Map.class));
                }
                
                cursor = json.has("nextCursor") ? json.get("nextCursor").asText() : null;
            }
        } while (cursor != null);

        return entities;
    }

    @SuppressWarnings("unchecked")
    private List<String> getRelationIdentifiers(Map<String, Object> entity, String relationName) {
    	System.out.println("RelationName: " + relationName);
        
    	List<Object> relations = null;
    	
    	Object relationsObj = entity.getOrDefault("relations", Collections.emptyMap());
    	
    	if (relationsObj instanceof Map) {
    	    Map<String, Object> relationsMap = (Map<String, Object>) relationsObj;
    	    
    	    Object relationValue = relationsMap.get(relationName);
    	    relations = (relationValue instanceof List) ? (List<Object>) relationValue : Collections.emptyList();
    	} 
    	else {
    	    relations = Collections.emptyList();
    	}
    	
        if (relations == null) {
        	return Collections.emptyList();
        }
        return relations.stream().map(Object::toString).collect(java.util.stream.Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getEntity(String blueprint, String identifier) throws IOException {
    	System.out.println("Getting Framework Entities");
    	
        String url = PORT_BASE_URL + "/blueprints/" + blueprint + "/entities/" + identifier;
        
        Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + accessToken).build();
        
        System.out.println("Request: " + request);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
            	throw new IOException("Failed to get entity: " + response);
            }
            Map<String, Object> map = mapper.readValue(response.body().string(), Map.class);
            
            System.out.println("Response: " + map);
            
            return map;
        }
    }

    private void updateServiceEolCount(String serviceId, int eolCount) throws IOException {
    	System.out.println("Update EOL Count");
    	
        String url = PORT_BASE_URL + "/blueprints/" + SERVICE_BLUEPRINT + "/entities/" + serviceId;
        
        ObjectNode payload = mapper.createObjectNode().set("properties", mapper.createObjectNode().put(TARGET_PROPERTY, eolCount));

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json"));

        Request request = new Request.Builder().url(url).patch(body).addHeader("Authorization", "Bearer " + accessToken).addHeader("Content-Type", "application/json").build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to update service " + serviceId + ": " + response);
            }
        }
    }
}
