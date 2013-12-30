package tafkacn;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 *
 * @author raymond.mak
 */
@Mojo(name = "jmx-exec")
public class JMXExecMojo extends AbstractMojo {

    static private final Attribute[] EMPTY_ATTRIBUTE_ARRAY = new Attribute[0];
    static private final Operation[] EMPTY_OPERATION_ARRAY = new Operation[0];

    final static private int TYPELABEL_BYTE = 0;
    final static private int TYPELABEL_SHORT = 1;
    final static private int TYPELABEL_INT = 2;
    final static private int TYPELABEL_LONG = 3;
    final static private int TYPELABEL_FLOAT = 4;
    final static private int TYPELABEL_DOUBLE = 5;
    final static private int TYPELABEL_BOOLEAN = 6;
    final static private int TYPELABEL_STRING = 7;

    final static private HashMap<String, Integer> typeLabelLookup
            = new HashMap<String, Integer>();

    static {
        typeLabelLookup.put(byte.class.getName(), TYPELABEL_BYTE);
        typeLabelLookup.put(Byte.class.getName(), TYPELABEL_BYTE);
        typeLabelLookup.put(short.class.getName(), TYPELABEL_SHORT);
        typeLabelLookup.put(Short.class.getName(), TYPELABEL_SHORT);
        typeLabelLookup.put(int.class.getName(), TYPELABEL_INT);
        typeLabelLookup.put(Integer.class.getName(), TYPELABEL_INT);
        typeLabelLookup.put(long.class.getName(), TYPELABEL_LONG);
        typeLabelLookup.put(Long.class.getName(), TYPELABEL_LONG);
        typeLabelLookup.put(float.class.getName(), TYPELABEL_FLOAT);
        typeLabelLookup.put(Float.class.getName(), TYPELABEL_FLOAT);
        typeLabelLookup.put(double.class.getName(), TYPELABEL_DOUBLE);
        typeLabelLookup.put(Double.class.getName(), TYPELABEL_DOUBLE);
        typeLabelLookup.put(boolean.class.getName(), TYPELABEL_BOOLEAN);
        typeLabelLookup.put(Boolean.class.getName(), TYPELABEL_BOOLEAN);
        typeLabelLookup.put(String.class.getName(), TYPELABEL_STRING);
    }

    @Parameter(required = true)
    public Server[] servers;

    @Parameter
    public Attribute[] attributes = EMPTY_ATTRIBUTE_ARRAY;

    @Parameter
    public Operation[] operations = EMPTY_OPERATION_ARRAY;

    @Parameter(required = true)
    public String objectName;

    @Parameter
    public int maxDegreeOfParallelism = 1;

    final private ArrayList<Exception> exceptions = new ArrayList<Exception>();

    private JMXServiceURL getRemoteJMXServiceURL(final Server server) throws Exception {
        getLog().info(String.format("Connecting to %s:%d...",
            server.host,
            server.jndiPort));

        return new JMXServiceURL(String.format(
            "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi",
            server.host,
            server.jndiPort));
    }

    private Object tryParse(final String type, final String value) throws Exception {
        switch (typeLabelLookup.get(type)) {
            case TYPELABEL_BYTE:
                return new Byte(value);
            case TYPELABEL_SHORT:
                return new Short(value);
            case TYPELABEL_INT:
                return new Integer(value);
            case TYPELABEL_LONG:
                return new Long(value);
            case TYPELABEL_FLOAT:
                return new Float(value);
            case TYPELABEL_DOUBLE:
                return new Double(value);
            case TYPELABEL_BOOLEAN:
                return new Boolean(value);
            case TYPELABEL_STRING:
                return value;
            default:
                return null;
        }
    }

    private String formatOperationInvocation(
        final MBeanOperationInfo operationInfo,
        final Object[] parameters) throws Exception {
        StringWriter operationWriter = new StringWriter();
        operationWriter.write(operationInfo.getName());
        operationWriter.write("(");
        String separator = "";
        MBeanParameterInfo[] parameterInfos = operationInfo.getSignature();

        for (int i = 0; i < parameters.length; i++) {
            MBeanParameterInfo parameterInfo = parameterInfos[i];

            operationWriter.write(separator);
            operationWriter.write(parameterInfo.getType());
            operationWriter.write(" ");
            operationWriter.write(parameterInfo.getName());
            operationWriter.write("=");
            operationWriter.write(parameters[i].toString());
            separator = ",";
        }
        operationWriter.write(")");
        return operationWriter.toString();

    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            ExecutorService threadPool = Executors.newFixedThreadPool(maxDegreeOfParallelism);
            for (final Server server : servers) {

                threadPool.submit(new Runnable() {

                    @Override
                    public void run() {
                        final String serverMessagePrefix = "[" + server.host + "] ";
                        try {
                            final JMXServiceURL jmxServiceURL = getRemoteJMXServiceURL(server);

							JMXConnector connector;
							if (server.credentials != null) {
								Map<String, String[]> env = new HashMap<String, String[]>();
								String[] creds = { server.credentials.user, server.credentials.password };
								env.put(JMXConnector.CREDENTIALS, creds);
								connector = JMXConnectorFactory.connect(jmxServiceURL, env);
							} else {
								connector = JMXConnectorFactory.connect(jmxServiceURL);
							}

                            final MBeanServerConnection mbeanServer =
                                connector.getMBeanServerConnection();

                            final ObjectName mbeanObjectName = new ObjectName(objectName);

                            final MBeanInfo mbeanInfo = mbeanServer.getMBeanInfo(mbeanObjectName);

                            if (attributes.length > 0) {
                                AttributeList attributeList = new AttributeList();
                                HashMap<String, MBeanAttributeInfo> attributeLookup
                                    = new HashMap<String, MBeanAttributeInfo>();
                                for (MBeanAttributeInfo attributeInfo : mbeanInfo.getAttributes()) {
                                    attributeLookup.put(attributeInfo.getName(), attributeInfo);

                                }
                                for (Attribute attribute : attributes) {
                                    if (attributeLookup.containsKey(attribute.name)) {
                                        MBeanAttributeInfo attributeInfo = attributeLookup.get(attribute.name);
                                        if (!attributeInfo.isWritable()) {
                                            throw new Exception(serverMessagePrefix + String.format("Attribute '%s' is not writable.", server.host, attribute));
                                        }
                                        else {
                                            String attributeType = attributeInfo.getType();
                                            if (typeLabelLookup.containsKey(attributeType)) {
                                                getLog().info(serverMessagePrefix + String.format("Apending '%s=%s' to the apply list (type=%s)",
                                                    attribute.name,
                                                    attribute.value,
                                                    attributeType));
                                                attributeList.add(new javax.management.Attribute(attribute.name, tryParse(attributeType, attribute.value)));
                                            }
                                            else {
                                                throw new Exception(serverMessagePrefix + String.format(
                                                    "Cannot apply attribute '%s' because %s' is not a supported attribute type",
                                                    attribute,
                                                    attributeType));
                                            }
                                        }
                                    }
                                    else {
                                        throw new Exception(
                                            serverMessagePrefix + String.format("'%s' is not an attribute of the MBean '%s'.",
                                                attribute.name,
                                                objectName));
                                    }
                                }
                                if (!attributeList.isEmpty()) {
                                    getLog().info(String.format("Committing attribute changes to MBean '%s'...",
                                            objectName));
                                    mbeanServer.setAttributes(mbeanObjectName, attributeList);
                                }
                            }
                            if (operations.length > 0) {
                                HashMap<String, List<MBeanOperationInfo>> operationInfoLookup =
                                    new HashMap<String, List<MBeanOperationInfo>>();
                                for (MBeanOperationInfo operationInfo : mbeanInfo.getOperations()) {
                                    List<MBeanOperationInfo> operationInfos = operationInfoLookup.get(operationInfo.getName());
                                    if (operationInfos == null) {
                                        operationInfos = new ArrayList<MBeanOperationInfo>();
                                        operationInfoLookup.put(operationInfo.getName(), operationInfos);
                                    }
                                    operationInfos.add(operationInfo);
                                }
                                for (Operation operation : operations) {
                                    if (operationInfoLookup.containsKey(operation.name)) {
                                        MBeanOperationInfo bestMatch = null;
                                        int minParameterCountDifference = Integer.MAX_VALUE;
                                        for (MBeanOperationInfo operationInfo : operationInfoLookup.get(operation.name)) {
                                            if (operationInfo.getSignature().length <= operation.parameters.length
                                                && (operation.parameters.length - operationInfo.getSignature().length) < minParameterCountDifference) {
                                                minParameterCountDifference = operation.parameters.length - operationInfo.getSignature().length;
                                                bestMatch = operationInfo;
                                            }
                                        }
                                        if (bestMatch == null) {
                                            throw new Exception(serverMessagePrefix + String.format("Could not find an overload of the '%s' operation that takes %d or fewer parameters.",
                                                operation.name,
                                                operation.parameters.length));

                                        }
                                        else {
                                            if (minParameterCountDifference > 0) {
                                                getLog().warn(serverMessagePrefix + String.format("The best match overload for operation '%s' accepts fewer parameters (%d) than specified (%d).", operation.name, bestMatch.getSignature().length, operation.parameters.length));
                                            }
                                            Object[] parsedParameters = new Object[bestMatch.getSignature().length];
                                            String[] parameterTypes = new String[bestMatch.getSignature().length];
                                            for (int i = 0; i < bestMatch.getSignature().length; i++) {
                                                String parameterType = bestMatch.getSignature()[i].getType();
                                                parameterTypes[i] = parameterType;
                                                if (typeLabelLookup.containsKey(parameterType)) {
                                                    parsedParameters[i] = tryParse(parameterType, operation.parameters[i]);
                                                }
                                                else {
                                                    throw new Exception(serverMessagePrefix + String.format("The operation '%s' contains an unsupported parameter type '%s'.", operation.name, parameterType));
                                                }
                                            }
                                            getLog().info(String.format("Invoking operation '%s' on MBean '%s'",
                                                formatOperationInvocation(bestMatch, parsedParameters),
                                                objectName));
                                            mbeanServer.invoke(mbeanObjectName, operation.name, parsedParameters, parameterTypes);
                                        }
                                    }
                                    else {
                                        throw new Exception(serverMessagePrefix + String.format("'%s' is not an operation of MBean '%s'.",
                                            operation.name,
                                            objectName));
                                    }
                                }
                            }
                        }
                        catch (Exception e) {
                            synchronized(exceptions) {
                                exceptions.add(new Exception(serverMessagePrefix + "Execution failed.",e));
                            }
                        }
                    }
                });
            }

            threadPool.shutdown();
            threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);

            if (!exceptions.isEmpty()) {
                for (Exception e : exceptions) {
                    getLog().error(e);
                }
                throw exceptions.get(0);
            }
        }
        catch(Exception e) {
            throw new MojoExecutionException("Execution failed", e);
        }

    }

}
