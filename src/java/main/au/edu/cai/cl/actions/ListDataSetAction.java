package au.edu.cai.cl.actions;

import java.net.URL;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import au.edu.cai.cl.TissueStackCLCommunicator;
import au.edu.cai.cl.json.JsonParser;


public class ListDataSetAction implements ClAction {
	private String session = null; 
	
	public boolean setMandatoryParameters(String[] args) {
		if (args.length != 1 ) {
			System.err.println("-- list needs a session!");
			return false;
		}
		this.session = args[0];
		return true;
	}

	public boolean needsSession() {
		return true;
	}

	public String getRequestUrl() {
		return "/server/?service=services&sub_service=metadata&action=dataset_list&session=" + this.session;
	}
	
	public ClActionResult performAction(final URL TissueStackServerURL) {
		String response = null;
		StringBuilder formattedResponse = new StringBuilder();
		try {
			final URL combinedURL = new URL(TissueStackServerURL.toString() + this.getRequestUrl());
			response = TissueStackCLCommunicator.sendHttpRequest(combinedURL, null);
			
			final JSONObject parseResponse = JsonParser.parseResponse(response);
			if (parseResponse.get("response") != null) { // success it seems
				JSONArray respObj = (JSONArray) parseResponse.get("response");
				@SuppressWarnings("unchecked")
				final JSONObject [] datasets = (JSONObject[]) respObj.toArray(new JSONObject[]{});
				for (JSONObject dataset : datasets) {
					formattedResponse.append("\tID:\t\t");
					formattedResponse.append(dataset.get("id"));
					formattedResponse.append("\n");
					formattedResponse.append("\tFILENAME:\t");
					formattedResponse.append(dataset.get("filename"));
					formattedResponse.append("\n");
					if (dataset.containsKey("description")) {
						formattedResponse.append("\tDESCRIPTION:\t");
						formattedResponse.append(dataset.get("description"));
						formattedResponse.append("\n");
					}
				}
				
			} else  // potential error
				formattedResponse = JsonParser.parseError(parseResponse, response);
		} catch(Exception any) {
			return new ClActionResult(ClAction.STATUS.ERROR, any.toString());
		}
		return new ClActionResult(ClAction.STATUS.SUCCESS, formattedResponse.toString());
	}

	public String getUsage() {
		return "--list";
	}

}
