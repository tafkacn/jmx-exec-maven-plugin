package tafkacn;

import org.apache.maven.plugins.annotations.Parameter;

/**
 *
 * @author raymond.mak
 */
public class Server {

    @Parameter(required = true)
    public String host;
    
    @Parameter(required = true)
    public int jndiPort;

}
