package com.ibm.cloudoe.samples;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.apache.http.util.TextUtils;
import org.w3c.dom.Document;
import org.w3c.dom.html.HTMLUListElement;

import com.alchemyapi.api.AlchemyAPI;
import com.alchemyapi.api.AlchemyAPI_KeywordParams;
import com.alchemyapi.api.AlchemyAPI_Params;
import com.alchemyapi.api.AlchemyAPI_TargetedSentimentParams;
import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;

/**
 * Servlet implementation class TwitterAnalysis
 */
@WebServlet("/TwitterAnalysis")
public class TwitterAnalysis extends HttpServlet {
	private static Logger logger = Logger.getLogger(TwitterAnalysis.class.getName());
	private static final long serialVersionUID = 1L;
	
	private static final int NUM_TWEETS = 500;
	private static final long MAX_TWEETS = 500;
	private String serviceName = "IBM Insights for Twitter-ee";
	// If running locally complete the variables below
	// with the information in VCAP_SERVICES
	private String baseURL = "https://84167b3f-22b2-4cd8-b94c-1422fc86ff80:UL0d63QXne@cdeservice.eu-gb.mybluemix.net";
	private String username = "84167b3f-22b2-4cd8-b94c-1422fc86ff80";
	private String password = "UL0d63QXne";
	
	private String alchemyKey = "8176235156ba22f3fbf93e61b686f28793dc42f8";
	
	
    public TwitterAnalysis() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		req.setCharacterEncoding("UTF-8");
		// create the request
		String tag = req.getParameter("tag");
		String context = req.getParameter("context");
		
		try {
			List<String> contextArr  = Arrays.asList(context.split(","));
			logger.log(Level.WARNING, "Input: " + tag + "-> "+contextArr.toString());
			HttpResponse httpResponse;
			JSONObject jsonResp;
			long countRes = 0, from = 0;
			String tweetsStr = "", tmpStrTweet = "", toWriteTweet = "", tmpTag = "";
			JSONArray tweetsArr;
			JSONObject tmpTweet;
			
			for (int h = 0; h < contextArr.size(); h++) {
				tmpTag = contextArr.get(h);
				tmpTag = formatTag(tmpTag.trim()+" "+tag);
				logger.log(Level.WARNING, "Searching tweets containing: " + tmpTag);
				httpResponse = callTwitterInsights("count",tmpTag);
				jsonResp = EntityToJSON(httpResponse.getEntity());
				countRes = (long)((JSONObject) jsonResp.get("search")).get("results");
				
				if (countRes > 0) {
					from = 0;
					if (countRes > NUM_TWEETS)
						from = countRes-NUM_TWEETS;
					
					httpResponse = callTwitterInsights("search",tmpTag,from);
					jsonResp = EntityToJSON(httpResponse.getEntity());
									
					tweetsArr = (JSONArray) jsonResp.get("tweets");
					tmpTweet = new JSONObject();
					toWriteTweet += "<b>Tweets from "+((String) ((JSONObject) ((JSONObject) tweetsArr.get(0)).get("message")).get("postedTime")).substring(0, 10)+" to "
							+ ""+((String) ((JSONObject) ((JSONObject) tweetsArr.get(tweetsArr.size()-1)).get("message")).get("postedTime")).substring(0, 10)+"</b><br>";
					
					for (int k = 0; k < tweetsArr.size(); k++) {
						tmpTweet = (JSONObject) tweetsArr.get(k);
						tmpStrTweet = (String) ((JSONObject) tmpTweet.get("message")).get("body");
						tweetsStr += " "+tmpStrTweet;
						toWriteTweet += "\n-> "+tmpStrTweet;
					}
				} else {
					logger.log(Level.SEVERE, "WARNING: No tweet found");
				}
									
				resp.setStatus(httpResponse.getStatusLine().getStatusCode());
			}
			

			String filePath = req.getSession().getServletContext().getRealPath("tweets");
			writeTweetsToFile(toWriteTweet, filePath);
			AlchemyAPI alchemyObj = AlchemyAPI.GetInstanceFromString(alchemyKey);
			Document doc = alchemyObj.TextGetTargetedSentiment(tweetsStr, tag);
			String outputStr = getStringFromDocument(doc);


			ServletOutputStream servletOutputStream = resp.getOutputStream();
			servletOutputStream.print(outputStr);
			servletOutputStream.flush();
			servletOutputStream.close();

		} catch (Exception e) {
			logger.log(Level.SEVERE, "Service error: " + e.getMessage(), e);
			resp.setStatus(HttpStatus.SC_BAD_GATEWAY);
		}
	}

	private String formatTag(String tag) {
		tag = tag.toLowerCase();
		while (tag.contains("  ")) {
			tag.replace("  ", " ");
		}
		String[] words = tag.split(" ");
		if (words[0].equalsIgnoreCase(words[1])) {
			tag = words[0];
		}
		return tag;
	}
	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		doGet(req, resp);
		
	}
	
	private HttpResponse callTwitterInsights(String type, String keyword, long from) throws URISyntaxException, ClientProtocolException, IOException {
			String qUrl = baseURL+"/api/v1/messages/"+type+"?q="+URLEncoder.encode(keyword,"UTF-8");
			qUrl += "&size="+NUM_TWEETS+"&from="+from;
			logger.log(Level.SEVERE, "URL -> "+qUrl);
			
			URI profileURI = new URI(qUrl).normalize();
	
			Request profileRequest = Request.Get(profileURI)
					.addHeader("Accept", "application/json");
	
			Executor executor = Executor.newInstance().auth(username, password);
			Response response = executor.execute(profileRequest);
			HttpResponse httpResponse  = response.returnResponse();
		return httpResponse;
	}
	
	private HttpResponse callTwitterInsights(String type, String keyword) throws URISyntaxException, ClientProtocolException, IOException {
		String qUrl = baseURL+"/api/v1/messages/"+type+"?q="+URLEncoder.encode(keyword,"UTF-8");
		URI profileURI = new URI(qUrl).normalize();
		logger.log(Level.SEVERE, "URL -> "+qUrl);
		Request profileRequest = Request.Get(profileURI)
				.addHeader("Accept", "application/json");

		Executor executor = Executor.newInstance().auth(username, password);
		Response response = executor.execute(profileRequest);
		HttpResponse httpResponse  = response.returnResponse();
	return httpResponse;
	}
	
	private void writeTweetsToFile(String tweets, String contextPath) throws IOException {

		String jsonFilePath = contextPath + "//savedTweets.txt";	
		FileWriter file = new FileWriter(jsonFilePath);
        try {
            file.write(tweets);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            file.flush();
            file.close();
        }
        
	}

	
	private JSONObject EntityToJSON(HttpEntity entity) {
		String s = "";
		JSONObject persInsOutput = null; 
		try {
			s = EntityUtils.toString(entity);
			persInsOutput = JSONObject.parse(s);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e1) {
			e1.printStackTrace();
		}
		return persInsOutput;
	}
	
	 private static String getStringFromDocument(Document doc) {
	        try {
	            DOMSource domSource = new DOMSource(doc);
	            StringWriter writer = new StringWriter();
	            StreamResult result = new StreamResult(writer);

	            TransformerFactory tf = TransformerFactory.newInstance();
	            Transformer transformer = tf.newTransformer();
	            transformer.transform(domSource, result);

	            return writer.toString();
	        } catch (TransformerException ex) {
	            ex.printStackTrace();
	            return null;
	        }
	    }
	
	@Override
	public void init() throws ServletException {
		super.init();
		processVCAPServices();
	}
	
	private void processVCAPServices() {
		logger.info("Processing VCAP_SERVICES");
		JSONObject sysEnv = getVCAPServices();
		if (sysEnv == null)
			return;
		logger.info("Looking for: " + serviceName);

		for (Object key : sysEnv.keySet()) {
			String keyString = (String) key;
			logger.info("found key: " + key);
			if (keyString.startsWith(serviceName)) {
				JSONArray services = (JSONArray) sysEnv.get(key);
				JSONObject service = (JSONObject) services.get(0);
				JSONObject credentials = (JSONObject) service
						.get("credentials");
				baseURL = (String) credentials.get("url");
				username = (String) credentials.get("username");
				password = (String) credentials.get("password");
				logger.info("baseURL  = " + baseURL);
				logger.info("username = " + username);
				logger.info("password = " + password);
			} else {
				logger.info("Doesn't match /^" + serviceName + "/");
			}
		}
	}

	/**
	 * Gets the <b>VCAP_SERVICES</b> environment variable and return it as a
	 * JSONObject.
	 * 
	 * @return the VCAP_SERVICES as Json
	 */
	private JSONObject getVCAPServices() {
		String envServices = System.getenv("VCAP_SERVICES");
		if (envServices == null)
			return null;
		JSONObject sysEnv = null;
		try {
			sysEnv = JSONObject.parse(envServices);
		} catch (IOException e) {
			// Do nothing, fall through to defaults
			logger.log(Level.SEVERE,
					"Error parsing VCAP_SERVICES: " + e.getMessage(), e);
		}
		return sysEnv;
	}
}
