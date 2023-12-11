//Camel API
// camel-k: dependency=mvn:org.json:json:20220924
// camel-k: dependency=mvn:org.apache.commons:commons-lang3:3.12.0

import org.apache.camel.*;
import org.apache.camel.builder.*;
import java.util.*;
import org.apache.commons.text.*;
import org.apache.commons.lang3.exception.*;
import org.slf4j.*;
import org.eclipse.microprofile.config.*;
import org.json.*;

/**
 * Utils class, used to transform JSON to XML in a simple way.
 * @author Timothé Rosaz
 */
public class JsonToXml extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(JsonToXml.class);
    private final static String XML_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    @Override
    public void configure() throws Exception {/* We only use it as a RouteBuilder to trigger the @BindToRegistry */}

    // Real processor method.
    // Should be call in the DSL with: <process ref="jsonToXml"/>
    //
    // Data is passed through the body. The input must be a valid JSON, and the output will be a valid XML.
    //
    @BindToRegistry
    public static Processor jsonToXml() {
        return new Processor() {
            private Config config = ConfigProvider.getConfig();

            public void process(Exchange exchange) throws Exception {
                // Read body
                String body = exchange.getIn().getBody(String.class);

                try {
                    // Convert
                    ParseConfiguration pc = new ParseConfiguration(config);
                    String xml = convertJsonToXml(body, pc);
    
                    // set body with the string
                    exchange.getIn().setBody(xml);
                    exchange.getIn().setHeader("Content-Type", "application/xml");
                } catch(JSONException e) {
                    LOG.error("Could not parse JSON: " + e.getMessage());
                    LOG.error("JSON = " + body.replace("\n",""));
                }
            }
        };
    }

    // Utils to load data from config properties.
    // Maps should have a syntax of "K_1=V_1,...,K_n=V_n"
    // Collections should have a syntax of "V_1,...,V_n"
    static class ConfigLoader {
        static void loadMap(Map<String, String> map, String configKey, Config config) {
            Optional<String> opt = config.getOptionalValue(configKey, String.class);
            if(opt.isEmpty())
                return;
            for(String token : opt.get().split(",")) {
                String[] p = token.split("=", 2);
                map.put(p[0], p[1]);
            }
        }
        static void loadCollection(Collection<String> coll, String configKey, Config config) {
            Optional<String> opt = config.getOptionalValue(configKey, String.class);
            if(opt.isEmpty())
                return;
            for(String token : opt.get().split(",")) {
                coll.add(token);
            }
        }
    }

    // Elements use to transport configuration through the processing
    //
    // Configurable values :
    // - [json2xml.names.arrays] : a MAP to customize anonymous array children naming.
    //     By default, children have the same name as their parents, but without the 's'.
    //     For instance, if the array is named "elements" then children will be named "element".
    //     By default, if an array name does not end with a 's' AND nothing as been put in this map,
    //     a RuntimeException will be thrown.
    //
    // - [json2xml.names.objects] : optional layer of transformations to rename some objects.
    //
    // - [json2xml.doublons] : in some cases, you may not want to do a JSON transformation.
    //     But if the JSON structure is poorly made, you can have a parent and a child having the same name.
    //     If it's not intended, this processor can remove them.
    //     For instance, if the xml creates a <foo><foo>VALUE</foo></foo>, put 'foo' in this list, and
    //     the obtained XML will be <foo>VALUE</foo>.
    //
    // - [json2xml.cDatas] : this list declare some paths as 'CDATA'. The expected value is a list of generic
    //     paths towards fields. For instance, "$.foo" or "$.some_array[].some_field" are valud inputs.
    //     A bad input won't do anything.
    //
    static class ParseConfiguration {
        private final static String CONFIG = "json2xml.";
        private final Map<String, String> arrays = new HashMap<>();
        private final Map<String, String> objects = new HashMap<>();
        private final Set<String> doublonsToRemove = new HashSet<>();
        private final Set<String> dataElements = new HashSet<>();

        public ParseConfiguration(Config config) {
            ConfigLoader.loadMap(arrays, CONFIG + "names.arrays", config);
            ConfigLoader.loadMap(objects, CONFIG + "names.objects", config);
            ConfigLoader.loadCollection(doublonsToRemove, CONFIG + "doublons", config);
            ConfigLoader.loadCollection(dataElements, CONFIG + "cDatas", config);
        }
        // Get the child name of an array.
        String getArrayChildrenName(String arrayName) {
            // optional overrides
            if(arrays.containsKey(arrayName)) {
                return arrays.get(arrayName);
            }
            // If ends with a 's', change it
            if(arrayName.endsWith("s") && arrayName.length() > 1) {
                return arrayName.substring(0, arrayName.length() - 1);
            }
            // Neither override or 's'-termination : it's an undefined case.
            throw new RuntimeException("Could not find and array-children for '" + arrayName + "'.");
        }
        // == Get properties
        String getObjectName(String name) {return objects.getOrDefault(name, name);}
        Set<String> doublons() {return Collections.unmodifiableSet(doublonsToRemove);}
        boolean isDataElement(String path) {return dataElements.contains(path);}
    }
    // Util-way of printing a XML prefix.
    private static String prefix(String v) {
        if(v == null) return "";
        return "<" + v + ">";
    }
    // Util-way of printing a XML suffix.
    private static String suffix(String v) {
        if(v == null) return "";
        return "</" + v + ">";
    }

    // Process a JSON string to an XML output.
    public static String convertJsonToXml(String json, ParseConfiguration config) {
        JSONObject jsonObject = new JSONObject(json);
        System.out.println("JSON = " + jsonObject);

        String str = toString(jsonObject, null, config, "$");

        // Remove optional doublons
        for(String doublon : config.doublons()) {
            String pr = prefix(doublon);
            String su = suffix(doublon);
            str = str.replace(pr + pr, pr).replace(su + su, su);
        }

        // Add the prefix to the produced XML.
        return XML_PREFIX + "\n" + str;
    }

    // Process a JSON object.
    private static String toString(JSONObject node, String name, ParseConfiguration config, String path) {
        StringBuilder content = new StringBuilder();

        // Process all children
        for(String entry : node.keySet()) {
            String childName = config.getObjectName(entry);;

            Object child = node.get(entry);
            content.append(toString(child, childName, config, path));
        }

        return prefix(name) + content + suffix(name);
    }

    // Process a JSON array.
    private static String toString(JSONArray node, String name, ParseConfiguration config, String path) {
        StringBuilder content = new StringBuilder();
        String childrenName = config.getArrayChildrenName(name);

        // Process all children
        for(Object child : node) {
            content.append(toString(child, childrenName, config, path));
        }

        return prefix(name) + content + suffix(name);
    }

    // Process any raw or JSON value.
    private static String toString(Object node, String name, ParseConfiguration config, String path) {
        path += "." + name;

        // Delegate to object
        if(node instanceof JSONObject) {
            return toString((JSONObject) node, name, config, path);
        }

        // Delegate to array
        if(node instanceof JSONArray) {
            path += "[]";
            return toString((JSONArray) node, name, config, path);
        }

        // Raw value
        String prefix = prefix(name);
        String suffix = suffix(name);
        // Test if should wrap it in a CDATA.
        if(config.isDataElement(path)) {
            prefix = prefix + "<![CDATA[ ";
            suffix = " ]]>" + suffix;
        }
        return prefix + node.toString() + suffix;
    }

}
